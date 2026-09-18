package app.prepa.garmin;

import app.prepa.activity.ActivityIngestService;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.garmin.client.GarminAuth;
import app.prepa.garmin.client.GarminClient;
import app.prepa.garmin.client.GarminException;
import app.prepa.garmin.client.GarminJetons;
import app.prepa.garmin.client.GarminMfaEnAttente;
import app.prepa.infra.AppProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Un passage de synchronisation, du compte Garmin jusqu'aux activites en base.
 *
 * <p>Le successeur direct de la boucle du synchroniseur Python, avec deux regles reprises telles
 * parce qu'elles portent de l'experience.
 *
 * <p>La premiere : un athlete en echec n'emporte pas les autres. Un mot de passe change chez l'un
 * ne doit pas priver les autres de leurs seances, et l'echec doit laisser une trace lisible
 * plutot qu'une exception remontee au planificateur.
 *
 * <p>La seconde : un seul passage a la fois, tous athletes confondus. Deux passages simultanes —
 * le passage complet et une relance depuis l'ecran d'exploitation, par exemple — doubleraient la
 * cadence des appels et feraient repondre 429 aux deux. La boucle sequentielle d'autrefois
 * l'interdisait par construction ; ici c'est un verrou qui s'en charge, et c'est aussi lui qui
 * rend honnete l'indicateur « une synchronisation est en cours ».
 */
@Component
public class GarminSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GarminSyncRunner.class);

    /**
     * On reprend la veille de la derniere synchronisation reussie.
     *
     * <p>Une montre synchronisee tardivement depose une seance datee d'hier apres que le passage
     * d'hier soit termine. Sans ce jour de recouvrement, elle ne serait jamais vue.
     */
    private static final int JOURS_RECOUVREMENT = 1;

    private final GarminSyncService sync;
    private final GarminAuth auth;
    private final GarminClient garmin;
    private final GarminNormalizer normalizer;
    private final ActivityIngestService ingestion;
    private final AthleteRepository athletes;
    private final GarminSyncRunRepository runs;
    private final int joursPremierSync;

    /** Ce que l'ecran d'exploitation affiche pendant qu'un passage tourne. */
    private volatile String athleteEnCours;

    private final ReentrantLock verrou = new ReentrantLock();

    public GarminSyncRunner(
            GarminSyncService sync,
            GarminAuth auth,
            GarminClient garmin,
            GarminNormalizer normalizer,
            ActivityIngestService ingestion,
            AthleteRepository athletes,
            GarminSyncRunRepository runs,
            AppProperties props) {
        this.sync = sync;
        this.auth = auth;
        this.garmin = garmin;
        this.normalizer = normalizer;
        this.ingestion = ingestion;
        this.athletes = athletes;
        this.runs = runs;
        this.joursPremierSync = props.garmin().joursPremierSync();
    }

    /** Vrai tant qu'un passage n'est pas termine. */
    public boolean enCours() {
        return verrou.isLocked();
    }

    /** Nom de l'athlete en cours de traitement, ou {@code null} au repos. */
    public String athleteEnCours() {
        return athleteEnCours;
    }

    /**
     * Un passage sur tous les athletes concernes.
     *
     * @param seulementDemandes ne retenir que ceux qui ont demande une synchronisation
     * @return le nombre d'athletes en echec, ou {@code -1} si un passage tournait deja
     */
    public int passage(DeclencheurSync declencheur, boolean seulementDemandes, String demandePar) {
        List<GarminSyncService.CibleSync> cibles = sync.ciblesASynchroniser(seulementDemandes);
        if (cibles.isEmpty()) {
            return 0;
        }
        return traiter(cibles, declencheur, demandePar, null);
    }

    /** Un passage sur un seul athlete, eventuellement sur une fenetre imposee. */
    public int passageCible(
            UUID athleteId, DeclencheurSync declencheur, String demandePar, LocalDate depuis) {
        return traiter(List.of(sync.cible(athleteId)), declencheur, demandePar, depuis);
    }

    private int traiter(
            List<GarminSyncService.CibleSync> cibles,
            DeclencheurSync declencheur,
            String demandePar,
            LocalDate depuis) {
        if (!verrou.tryLock()) {
            log.info("Synchronisation déjà en cours, passage ignoré");
            return -1;
        }
        int echecs = 0;
        try {
            for (GarminSyncService.CibleSync cible : cibles) {
                athleteEnCours = cible.athlete();
                if (!synchroniser(cible, declencheur, demandePar, depuis)) {
                    echecs++;
                }
            }
        } finally {
            athleteEnCours = null;
            verrou.unlock();
        }
        return echecs;
    }

    /** @return vrai si l'athlete a ete synchronise sans encombre */
    private boolean synchroniser(
            GarminSyncService.CibleSync cible,
            DeclencheurSync declencheur,
            String demandePar,
            LocalDate depuis) {
        GarminSyncRun trace = new GarminSyncRun(cible.athleteId(), declencheur, demandePar);
        LocalDate debut = fenetreDeDepart(cible, depuis);
        LocalDate fin = LocalDate.now(ZoneId.systemDefault());
        trace.setFenetreDu(debut);
        trace.setFenetreAu(fin);
        runs.save(trace);
        sync.marquerEnCours(cible.athleteId());

        StatutSync statut;
        String message;
        String displayName = null;
        Integer importees = null;

        try {
            GarminJetons jetons = auth.ouvrir(cible.email(), cible.motDePasse(), cible.jetons());
            sync.enregistrerJetons(cible.athleteId(), jetons);

            displayName = garmin.displayName(jetons);
            verifierIdentite(cible, displayName);

            List<JsonNode> resumes = garmin.activites(jetons, debut, fin);
            log.info("Synchronisation de {} : {} séance(s) du {} au {}",
                    cible.athlete(), resumes.size(), debut, fin);

            if (resumes.isEmpty()) {
                statut = StatutSync.OK;
                message = "Aucune activité nouvelle";
                trace.setRecues(0);
                trace.setImportees(0);
                trace.setMisesAJour(0);
                trace.setDoublons(0);
                importees = 0;
            } else {
                GarminDtos.ResultatIngestion resultat = ingerer(cible.athleteId(), jetons, resumes);
                trace.setRecues(resultat.recues());
                trace.setImportees(resultat.importees());
                trace.setMisesAJour(resultat.misesAJour());
                trace.setDoublons(resultat.doublons());
                importees = resultat.importees();
                statut = StatutSync.OK;
                message = resultat.importees() + " importée(s), " + resultat.misesAJour() + " enrichie(s)";
            }
        } catch (GarminMfaEnAttente e) {
            sync.memoriserContexteMfa(cible.athleteId(), e.contexte());
            statut = e.statut();
            message = e.getMessage();
            log.warn("Vérification en deux étapes demandée pour {}", cible.athlete());
        } catch (GarminException e) {
            statut = e.statut();
            message = tronquer(e.getMessage());
            log.error("Synchronisation impossible pour {} : {}", cible.athlete(), message);
        } catch (RuntimeException e) {
            // Le filet : une erreur imprevue sur un athlete ne doit pas interrompre la tournee.
            statut = StatutSync.ERREUR;
            message = tronquer(e.getMessage());
            log.error("Erreur inattendue sur {}", cible.athlete(), e);
        }

        trace.setStatut(statut);
        trace.setMessage(message);
        trace.setTermineA(java.time.Instant.now());
        runs.save(trace);

        // Le nom du compte n'est remonte qu'en cas de succes : une identite ne se fige pas sur un echec.
        sync.enregistrerResultat(
                new GarminSyncService.ResultatSync(cible.athleteId(), statut, message, importees),
                statut == StatutSync.OK ? displayName : null);
        return statut == StatutSync.OK;
    }

    private GarminDtos.ResultatIngestion ingerer(
            UUID athleteId, GarminJetons jetons, List<JsonNode> resumes) {
        List<GarminDtos.ActiviteBrute> lot = new ArrayList<>();
        for (JsonNode resume : resumes) {
            long id = resume.path("activityId").asLong(0);
            Map<String, JsonNode> blocs = id == 0 ? Map.of() : garmin.details(jetons, id);
            GarminDtos.ActiviteBrute activite = normalizer.normaliser(resume, blocs);
            if (activite != null) {
                lot.add(activite);
            }
        }
        Athlete athlete = athletes.findById(athleteId)
                .orElseThrow(() -> app.prepa.infra.ApiException.notFound("Athlète"));
        return ingestion.ingerer(athlete, lot);
    }

    /**
     * Refuse de continuer si le compte connecte n'est pas celui memorise sur l'athlete.
     *
     * <p>Sans ce garde-fou, une erreur d'identifiants ecrirait les seances d'un athlete dans
     * l'historique d'un autre, et rien ne le signalerait avant longtemps. Il est volontairement
     * place apres l'ouverture de session et jamais avant : c'est le compte reellement connecte
     * qu'on verifie, pas celui qu'on croit avoir demande.
     */
    private void verifierIdentite(GarminSyncService.CibleSync cible, String connecte) {
        if (connecte == null || connecte.isBlank()) {
            log.warn("Compte Garmin non identifiable pour {} : vérification ignorée", cible.athlete());
            return;
        }
        String attendu = cible.garminDisplayName();
        if (attendu != null && !attendu.equals(connecte)) {
            throw GarminException.identite(
                    "Mauvais compte Garmin : " + connecte + " connecté, " + attendu
                            + " attendu pour " + cible.athlete());
        }
    }

    private LocalDate fenetreDeDepart(GarminSyncService.CibleSync cible, LocalDate depuis) {
        if (depuis != null) {
            return depuis;
        }
        return Optional.ofNullable(cible.derniereSync())
                .map(instant -> instant.atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .minusDays(JOURS_RECOUVREMENT))
                .orElseGet(() -> LocalDate.now(ZoneId.systemDefault()).minusDays(joursPremierSync));
    }

    /** La colonne du message est libre, mais un pavé d'exception n'aide personne a l'ecran. */
    private static String tronquer(String message) {
        if (message == null) {
            return "Erreur sans message";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /** Supprime les traces trop anciennes pour eclairer quoi que ce soit. */
    @org.springframework.transaction.annotation.Transactional
    public int purger(int retentionJours) {
        return runs.purger(java.time.Instant.now().minus(retentionJours, ChronoUnit.DAYS));
    }
}
