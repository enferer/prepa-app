package app.prepa.garmin;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.auth.ServiceKeyService;
import app.prepa.garmin.client.GarminAuth;
import app.prepa.garmin.client.GarminException;
import app.prepa.garmin.client.GarminJetons;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pilotage de la synchronisation Garmin pour un athlete.
 *
 * <p>La demande ne pose plus un drapeau qu'un processus tiers releverait : elle lance le travail,
 * dans un fil de fond, et repond aussitot. Le drapeau subsiste comme filet — si le serveur
 * redemarre en plein passage, la releve planifiee reprendra la demande.
 */
@RestController
@RequestMapping("/api/v1")
public class GarminSyncController {

    private final GarminSyncService sync;
    private final GarminSyncRunner runner;
    private final GarminAuth auth;
    private final AthleteService athletes;
    private final org.springframework.core.task.TaskExecutor executeur;

    public GarminSyncController(
            GarminSyncService sync,
            GarminSyncRunner runner,
            GarminAuth auth,
            AthleteService athletes,
            @org.springframework.beans.factory.annotation.Qualifier(GarminSyncConfig.EXECUTEUR)
                    org.springframework.core.task.TaskExecutor executeur) {
        this.sync = sync;
        this.runner = runner;
        this.auth = auth;
        this.athletes = athletes;
        this.executeur = executeur;
    }

    /**
     * Lance une synchronisation et rend la main.
     *
     * <p>Repond 202 : le travail a ete accepte, pas termine. Son avancement se lit sur
     * {@code sync-status}, qui passe par {@link StatutSync#EN_COURS}.
     */
    @PostMapping("/athletes/{athleteId}/sync")
    public ResponseEntity<GarminSyncService.EtatSync> demander(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis) {
        Principal principal = exigerSynchroniseur(athleteId);
        GarminSyncService.EtatSync etat = sync.demanderSync(athleteId);
        String demandePar = principal.nom();
        executeur.execute(() ->
                runner.passageCible(athleteId, DeclencheurSync.DEMANDE, demandePar, depuis));
        return ResponseEntity.accepted().body(etat);
    }

    @GetMapping("/athletes/{athleteId}/sync-status")
    public GarminSyncService.EtatSync etat(@PathVariable UUID athleteId) {
        athletes.modifiable(athleteId, CurrentPrincipal.get());
        return sync.etat(athleteId);
    }

    /** Relie un compte Garmin a un athlete. Les identifiants sont chiffres avant d'etre stockes. */
    @PutMapping("/athletes/{athleteId}/garmin-credentials")
    public GarminSyncService.EtatSync relierCompte(
            @PathVariable UUID athleteId, @Valid @RequestBody IdentifiantsRequest req) {
        athletes.modifiable(athleteId, CurrentPrincipal.get());
        sync.enregistrerIdentifiants(athleteId, req.email(), req.motDePasse());
        return sync.etat(athleteId);
    }

    /**
     * Transmet le code de verification en deux etapes reclame par Garmin.
     *
     * <p>Ce cas se reglait autrefois en lancant le synchroniseur a la main dans un terminal sur le
     * serveur. Sans terminal, il faut bien une route : la session laissee en suspens est reprise,
     * le code poste, et les jetons enregistres pour que le probleme ne se represente pas avant un
     * an.
     */
    @PostMapping("/athletes/{athleteId}/garmin-mfa")
    public GarminSyncService.EtatSync validerMfa(
            @PathVariable UUID athleteId, @Valid @RequestBody MfaRequest req) {
        exigerSynchroniseur(athleteId);
        String contexte = sync.contexteMfa(athleteId);
        try {
            GarminJetons jetons = auth.validerMfa(contexte, req.code());
            sync.enregistrerJetons(athleteId, jetons);
            sync.enregistrerResultat(
                    new GarminSyncService.ResultatSync(
                            athleteId, StatutSync.OK, "Vérification en deux étapes validée", 0),
                    null);
        } catch (GarminException e) {
            throw ApiException.invalide(e.getMessage());
        }
        return sync.etat(athleteId);
    }

    public record IdentifiantsRequest(@NotBlank String email, @NotBlank String motDePasse) {}

    public record MfaRequest(@NotBlank String code) {}

    /**
     * Declencher une synchronisation, c'est ecrire des donnees de terrain au nom d'un athlete :
     * cela reste reserve aux clients machine portant le droit d'ingestion.
     */
    private Principal exigerSynchroniseur(UUID athleteId) {
        Principal principal = CurrentPrincipal.get();
        if (!principal.estCoach() || !principal.aLeScope(ServiceKeyService.SCOPE_INGEST)) {
            throw ApiException.forbidden("Réservé au service de synchronisation");
        }
        athletes.modifiable(athleteId, principal);
        return principal;
    }
}
