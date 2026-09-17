package app.prepa.garmin;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.auth.ServiceKeyService;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Synchronisation Garmin : demandes cote utilisateur, releve cote worker. */
@RestController
@RequestMapping("/api/v1")
public class GarminSyncController {

    private final GarminSyncService sync;
    private final AthleteService athletes;

    public GarminSyncController(GarminSyncService sync, AthleteService athletes) {
        this.sync = sync;
        this.athletes = athletes;
    }

    /** Demande une synchronisation immediate. Le worker la relevera a son passage suivant. */
    @PostMapping("/athletes/{athleteId}/sync")
    public GarminSyncService.EtatSync demander(@PathVariable UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
        return sync.demanderSync(athleteId);
    }

    @GetMapping("/athletes/{athleteId}/sync-status")
    public GarminSyncService.EtatSync etat(@PathVariable UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
        return sync.etat(athleteId);
    }

    /** Relie un compte Garmin a un athlete. Les identifiants sont chiffres avant d'etre stockes. */
    @PutMapping("/athletes/{athleteId}/garmin-credentials")
    public GarminSyncService.EtatSync relierCompte(
            @PathVariable UUID athleteId, @Valid @RequestBody IdentifiantsRequest req) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
        sync.enregistrerIdentifiants(athleteId, req.email(), req.motDePasse());
        return sync.etat(athleteId);
    }

    /**
     * Athletes a synchroniser, avec leurs identifiants.
     *
     * <p>Seul endroit ou les secrets Garmin ressortent de la base : reserve au worker, par une
     * cle de service portant le droit d'ingestion.
     */
    @GetMapping("/ingest/targets")
    public List<GarminSyncService.CibleSync> cibles(
            @RequestParam(defaultValue = "false") boolean seulementDemandes) {
        exigerWorker();
        return sync.ciblesASynchroniser(seulementDemandes);
    }

    @PostMapping("/ingest/sync-status")
    public void remonterResultat(@Valid @RequestBody ResultatRequest req) {
        exigerWorker();
        sync.enregistrerResultat(
                new GarminSyncService.ResultatSync(
                        req.athleteId(), req.statut(), req.message(), req.activitesRecuperees()),
                req.garminDisplayName());
    }

    public record IdentifiantsRequest(@NotBlank String email, @NotBlank String motDePasse) {}

    public record ResultatRequest(
            UUID athleteId,
            String statut,
            String message,
            Integer activitesRecuperees,
            String garminDisplayName) {}

    private void exigerWorker() {
        Principal principal = CurrentPrincipal.get();
        if (!principal.estCoach() || !principal.aLeScope(ServiceKeyService.SCOPE_INGEST)) {
            throw ApiException.forbidden("Réservé au service de synchronisation");
        }
    }
}
