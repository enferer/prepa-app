package app.prepa.cycle;

import app.prepa.domain.StatutCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Constate chaque jour les seances passees qu'aucune activite n'est venue accomplir.
 *
 * <p>Qu'une seance ait eu lieu se sait a l'ingestion : une activite arrive, on la rattache.
 * Qu'elle n'ait <em>pas</em> eu lieu ne s'apprend d'aucun evenement — c'est le temps qui
 * passe qui le dit, et rien d'autre. Faute de quelqu'un pour poser ce constat, une seance
 * manquee restait « a venir » indefiniment : l'athlete voyait un semainier ou rien ne
 * s'etait jamais rien passe, et le coach n'avait aucun ecart a interroger.
 *
 * <p>Le rattacher a l'ingestion aurait paru plus economique, mais aurait laisse de cote
 * exactement les athletes concernes — ceux dont il n'arrive rien.
 */
@Component
public class ConstatPlanifie {

    private static final Logger log = LoggerFactory.getLogger(ConstatPlanifie.class);

    private final ConstatService constats;
    private final CycleRepository cycles;

    public ConstatPlanifie(ConstatService constats, CycleRepository cycles) {
        this.constats = constats;
        this.cycles = cycles;
    }

    /**
     * Au petit matin, apres la nuit qu'on laisse aux montres pour se synchroniser.
     *
     * <p>Le service laisse en plus un jour plein de battement : ce passage ne tranche donc
     * que sur l'avant-veille, jamais sur la seance d'hier soir.
     */
    @Scheduled(cron = "${prepa.constats.cron:0 30 5 * * *}", zone = "Europe/Paris")
    public void constaterLesAbsences() {
        int total = 0;
        for (Cycle cycle : cycles.findByStatut(StatutCycle.ACTIF)) {
            try {
                total += constats.constaterAbsences(cycle.getAthleteId());
            } catch (RuntimeException e) {
                // Un athlete en echec ne doit pas empecher les autres d'etre traites.
                log.error("Constat des absences impossible pour {}", cycle.getAthleteId(), e);
            }
        }
        if (total > 0) {
            log.info("{} seance(s) constatee(s) non realisee(s)", total);
        }
    }
}
