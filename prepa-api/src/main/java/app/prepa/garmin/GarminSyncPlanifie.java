package app.prepa.garmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La cadence de la synchronisation Garmin.
 *
 * <p>Elle etait portee par la boucle d'un conteneur Python, invisible depuis l'API et reglee par
 * une variable d'environnement. La voici a cote du constat quotidien des seances non realisees,
 * dans le seul processus qui compte.
 *
 * <p>Deux rythmes, comme avant, et pour la meme raison. Le passage complet balaie tous les
 * athletes relies : c'est lui qui ramene les seances de celui qui court et ne demande rien,
 * c'est-a-dire le cas courant. La releve des demandes tourne bien plus souvent mais ne regarde
 * que le drapeau : elle sert de filet quand une synchronisation lancee a la main n'a pas abouti,
 * typiquement parce que le serveur a redemarre au milieu.
 */
@Component
public class GarminSyncPlanifie {

    private static final Logger log = LoggerFactory.getLogger(GarminSyncPlanifie.class);

    private final GarminSyncRunner runner;
    private final int retentionJours;

    public GarminSyncPlanifie(
            GarminSyncRunner runner, @Value("${prepa.sync.retention-jours:90}") int retentionJours) {
        this.runner = runner;
        this.retentionJours = retentionJours;
    }

    @Scheduled(cron = "${prepa.sync.cron-demandes:0 * * * * *}", zone = "Europe/Paris")
    public void releverLesDemandes() {
        executer(DeclencheurSync.DEMANDE, true);
    }

    @Scheduled(cron = "${prepa.sync.cron-complet:0 0/30 * * * *}", zone = "Europe/Paris")
    public void passageComplet() {
        executer(DeclencheurSync.PLANIFIE, false);
    }

    /**
     * Au petit matin, apres le passage complet de la nuit.
     *
     * <p>L'historique sert a comprendre une panne recente ou a relire le mois ecoule ; au-dela il
     * ne raconte plus rien que les seances elles-memes ne disent mieux.
     */
    @Scheduled(cron = "${prepa.sync.cron-purge:0 45 4 * * *}", zone = "Europe/Paris")
    public void purgerLHistorique() {
        int supprimes = runner.purger(retentionJours);
        if (supprimes > 0) {
            log.info("{} trace(s) de synchronisation purgée(s)", supprimes);
        }
    }

    private void executer(DeclencheurSync declencheur, boolean seulementDemandes) {
        try {
            int echecs = runner.passage(declencheur, seulementDemandes, null);
            if (echecs > 0) {
                log.warn("Passage {} terminé avec {} athlète(s) en échec", declencheur, echecs);
            }
        } catch (RuntimeException e) {
            // Le planificateur ne doit jamais remonter d'exception : une tache qui echoue bruyamment
            // reste planifiee, mais rien ne garantit qu'on le remarque avant longtemps.
            log.error("Passage {} en échec", declencheur, e);
        }
    }
}
