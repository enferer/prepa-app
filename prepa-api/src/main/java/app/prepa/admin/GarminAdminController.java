package app.prepa.admin;

import app.prepa.garmin.DeclencheurSync;
import app.prepa.garmin.GarminSyncConfig;
import app.prepa.garmin.GarminSyncRun;
import app.prepa.garmin.GarminSyncRunRepository;
import app.prepa.garmin.GarminSyncRunner;
import app.prepa.garmin.GarminSyncService;
import app.prepa.garmin.StatutSync;
import app.prepa.athlete.AthleteRepository;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.infra.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exploitation de la synchronisation Garmin : ce qui tourne, ce qui s'est passe, et de quoi
 * relancer.
 *
 * <p>Sous {@code /api/v1/admin}, donc reserve au role ADMIN par la chaine de filtres. Ce choix de
 * prefixe n'est pas cosmetique : un administrateur connecte porte un jeton sans aucun scope, et ne
 * peut donc pas franchir les gardes des routes reservees au coach. Le routage regle la question la
 * ou elle se pose, plutot que d'assouplir un controle d'acces eprouve.
 *
 * <p>En contrepartie, une cle de service ne peut pas appeler ces routes — elle dispose deja de
 * {@code POST /athletes/{id}/sync}, qui fait le meme travail pour un athlete.
 */
@RestController
@RequestMapping("/api/v1/admin/garmin")
public class GarminAdminController {

    /** Au-dela, l'historique ne se lit plus : il se filtre. */
    private static final int LIMITE_MAX = 200;

    private static final int LIMITE_DEFAUT = 50;

    private final GarminSyncService sync;
    private final GarminSyncRunner runner;
    private final GarminSyncRunRepository runs;
    private final AthleteRepository athletes;
    private final TaskExecutor executeur;

    public GarminAdminController(
            GarminSyncService sync,
            GarminSyncRunner runner,
            GarminSyncRunRepository runs,
            AthleteRepository athletes,
            @Qualifier(GarminSyncConfig.EXECUTEUR) TaskExecutor executeur) {
        this.sync = sync;
        this.runner = runner;
        this.runs = runs;
        this.athletes = athletes;
        this.executeur = executeur;
    }

    /** Ce qui tourne maintenant, et l'etat de chaque compte relie. */
    @GetMapping("/etat")
    public EtatGlobal etat() {
        List<GarminSyncService.EtatSync> etats = sync.etatDeTous();
        List<CompteGarmin> comptes = etats.stream()
                .map(e -> new CompteGarmin(
                        e.athleteId(),
                        e.athlete(),
                        garminDisplayName(e.athleteId()),
                        e.derniereSync(),
                        e.dernierStatut(),
                        e.dernierMessage(),
                        e.syncDemande()))
                // Ce qui reclame une action remonte en tete : c'est le motif d'ouverture de l'ecran.
                .sorted((a, b) -> Integer.compare(urgence(b.dernierStatut()), urgence(a.dernierStatut())))
                .toList();
        return new EtatGlobal(runner.enCours(), runner.athleteEnCours(), comptes);
    }

    /** Les derniers passages, plus recents d'abord. */
    @GetMapping("/runs")
    public List<PassageResponse> passages(
            @RequestParam(required = false) UUID athleteId,
            @RequestParam(required = false) Integer limit) {
        Limit limite = Limit.of(Math.min(limit == null ? LIMITE_DEFAUT : limit, LIMITE_MAX));
        List<GarminSyncRun> trouves = athleteId == null
                ? runs.findByOrderByDemarreADesc(limite)
                : runs.findByAthleteIdOrderByDemarreADesc(athleteId, limite);

        // Un seul aller-retour pour les noms, plutot qu'une lecture par ligne d'historique.
        Map<UUID, String> noms = new HashMap<>();
        athletes.findAllById(trouves.stream().map(GarminSyncRun::getAthleteId).distinct().toList())
                .forEach(athlete -> noms.put(athlete.getId(), athlete.getDisplayName()));

        return trouves.stream()
                .map(run -> PassageResponse.from(run, noms.get(run.getAthleteId())))
                .toList();
    }

    /** Relance un passage complet sur tous les athletes relies. */
    @PostMapping("/sync")
    public ResponseEntity<Void> toutSynchroniser() {
        return lancer(() -> runner.passage(DeclencheurSync.MANUEL, false, auteur()));
    }

    /** Relance un athlete precis, eventuellement sur une fenetre imposee. */
    @PostMapping("/athletes/{athleteId}/sync")
    public ResponseEntity<Void> synchroniser(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis) {
        // Verifie que le compte existe avant d'accepter : un 202 sur un athlete sans compte Garmin
        // ne serait detectable qu'en allant relire l'historique.
        sync.cible(athleteId);
        String auteur = auteur();
        return lancer(() -> runner.passageCible(athleteId, DeclencheurSync.MANUEL, auteur, depuis));
    }

    /**
     * Accepte, ou refuse net si un passage tourne deja.
     *
     * <p>Empiler les demandes derriere le verrou donnerait l'illusion d'avoir ete entendu, pour un
     * travail qui ferait doublon avec celui en cours.
     */
    private ResponseEntity<Void> lancer(Runnable travail) {
        if (runner.enCours()) {
            throw ApiException.conflit("Une synchronisation est déjà en cours");
        }
        executeur.execute(travail);
        return ResponseEntity.accepted().build();
    }

    private String auteur() {
        return CurrentPrincipal.get().nom();
    }

    private String garminDisplayName(UUID athleteId) {
        return athletes.findById(athleteId)
                .map(app.prepa.athlete.Athlete::getGarminDisplayName)
                .orElse(null);
    }

    /** Un statut qui appelle un geste humain passe devant. */
    private static int urgence(StatutSync statut) {
        if (statut == null) {
            return 1;
        }
        return switch (statut) {
            case IDENTITE_KO, MFA_REQUISE -> 4;
            case AUTH_ERROR -> 3;
            case ERREUR -> 2;
            case EN_COURS, OK -> 0;
        };
    }

    public record EtatGlobal(boolean enCours, String athleteEnCours, List<CompteGarmin> comptes) {}

    public record CompteGarmin(
            UUID athleteId,
            String athlete,
            String garminDisplayName,
            Instant derniereSync,
            StatutSync dernierStatut,
            String dernierMessage,
            boolean syncDemande) {}

    public record PassageResponse(
            UUID id,
            UUID athleteId,
            String athlete,
            Instant demarreA,
            Instant termineA,
            Long dureeSec,
            DeclencheurSync declencheur,
            String demandePar,
            LocalDate fenetreDu,
            LocalDate fenetreAu,
            StatutSync statut,
            String message,
            Integer recues,
            Integer importees,
            Integer misesAJour,
            Integer doublons) {

        static PassageResponse from(GarminSyncRun run, String athlete) {
            Long duree = run.getTermineA() == null
                    ? null
                    : Duration.between(run.getDemarreA(), run.getTermineA()).toSeconds();
            return new PassageResponse(
                    run.getId(),
                    run.getAthleteId(),
                    athlete,
                    run.getDemarreA(),
                    run.getTermineA(),
                    duree,
                    run.getDeclencheur(),
                    run.getDemandePar(),
                    run.getFenetreDu(),
                    run.getFenetreAu(),
                    run.getStatut(),
                    run.getMessage(),
                    run.getRecues(),
                    run.getImportees(),
                    run.getMisesAJour(),
                    run.getDoublons());
        }
    }
}
