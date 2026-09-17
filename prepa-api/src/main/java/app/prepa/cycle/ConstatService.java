package app.prepa.cycle;

import app.prepa.activity.Activity;
import app.prepa.activity.ActivityRepository;
import app.prepa.domain.StatutSeance;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Constate ce qui a eu lieu, sans le juger.
 *
 * <p>Une seance dont l'activite est arrivee sur la montre est realisee : l'athlete n'a pas a
 * attendre le point hebdomadaire pour la voir comptee, et le coach n'a pas a cocher a la main
 * ce que les donnees disent deja. Ce service pose donc {@code REALISEE} des l'ingestion, et
 * {@code NON_REALISEE} quand une date passe sans que rien ne vienne.
 *
 * <p>Il ne pose jamais {@code ANALYSEE} : c'est un jugement, et il appartient au coach. Il ne
 * revient jamais non plus sur une decision deja prise — une seance analysee, deplacee ou
 * annulee ne bouge plus.
 */
@Service
public class ConstatService {

    private static final Logger log = LoggerFactory.getLogger(ConstatService.class);

    /**
     * Ecart de date tolere pour rattacher une activite a une seance.
     * Une sortie decalee d'un jour reste la seance prevue ; au-dela de deux, c'en est une autre.
     */
    private static final int JOURS_TOLERANCE = 2;

    /**
     * Delai laisse a une activite pour arriver avant de constater qu'elle n'est pas venue.
     * Une montre se synchronise parfois le lendemain : declarer une seance non faite le soir
     * meme serait souvent faux, et ferait poser au coach une question sans objet.
     */
    private static final int JOURS_BATTEMENT = 1;

    private final PlannedSessionRepository seances;
    private final ActivityRepository activities;
    private final CycleRepository cycles;

    public ConstatService(
            PlannedSessionRepository seances, ActivityRepository activities, CycleRepository cycles) {
        this.seances = seances;
        this.activities = activities;
        this.cycles = cycles;
    }

    /**
     * Rattache une activite fraichement arrivee a la seance qui l'attendait, s'il y en a une.
     *
     * <p>Appele a chaque ingestion. Sans correspondance, l'activite reste une sortie hors plan
     * — ce qui n'a rien d'anormal.
     */
    @Transactional
    public Optional<PlannedSession> constaterArrivee(Activity activite) {
        if (!activite.getType().estCourseAPied()) {
            return Optional.empty();
        }
        Optional<Cycle> cycle = cycles.couvrant(activite.getAthleteId(), activite.getDateLocale()).stream()
                .findFirst();
        if (cycle.isEmpty()) {
            return Optional.empty();
        }

        LocalDate date = activite.getDateLocale();
        List<PlannedSession> candidates = seances
                .entre(cycle.get().getId(), date.minusDays(JOURS_TOLERANCE), date.plusDays(JOURS_TOLERANCE))
                .stream()
                .filter(s -> s.getActivityId() == null)
                .filter(PlannedSession::estCourseAPied)
                .filter(s -> s.getStatut().accepteUnConstatAutomatique())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        PlannedSession choisie = meilleureCorrespondance(candidates, activite);
        choisie.rapprocherDe(activite.getId(), false);
        choisie.setStatut(StatutSeance.REALISEE);
        seances.save(choisie);
        log.debug("Activite {} rattachee a la seance {}", activite.getId(), choisie.getId());
        return Optional.of(choisie);
    }

    /**
     * Passe en {@code NON_REALISEE} les seances dont la date est passee sans qu'une activite
     * ne soit venue.
     *
     * <p>Ce n'est pas un reproche : c'est le constat qui permettra au coach de demander ce qui
     * s'est passe. On laisse un jour de battement — une montre peut se synchroniser en retard,
     * et declarer une seance non faite le soir meme serait souvent faux.
     */
    @Transactional
    public int constaterAbsences(UUID athleteId) {
        Optional<Cycle> cycle = cycles.findByAthleteIdAndStatut(
                athleteId, app.prepa.domain.StatutCycle.ACTIF);
        if (cycle.isEmpty()) {
            return 0;
        }
        // Borne inclusive : on s'arrete a l'avant-veille, ce qui laisse un jour plein de
        // battement a une montre qui se synchronise en retard.
        LocalDate limite = LocalDate.now().minusDays(JOURS_BATTEMENT + 1L);

        List<PlannedSession> echues = seances
                .entre(cycle.get().getId(), cycle.get().getDateDebut(), limite)
                .stream()
                .filter(s -> s.getStatut() == StatutSeance.A_VENIR)
                .filter(PlannedSession::estCourseAPied)
                .filter(s -> s.getActivityId() == null)
                .toList();

        echues.forEach(seance -> {
            seance.setStatut(StatutSeance.NON_REALISEE);
            seances.save(seance);
        });
        return echues.size();
    }

    /**
     * Rejoue le rattachement sur une periode : utile apres un import d'historique, ou apres
     * une refonte du plan qui a cree des seances la ou des activites existaient deja.
     */
    @Transactional
    public int rattraper(UUID athleteId, LocalDate debut, LocalDate fin) {
        int rattachees = 0;
        for (Activity activite : activities.entre(athleteId, debut, fin)) {
            if (seances.findByActivityId(activite.getId()).isEmpty()
                    && constaterArrivee(activite).isPresent()) {
                rattachees++;
            }
        }
        rattachees += constaterAbsences(athleteId);
        return rattachees;
    }

    /**
     * Parmi plusieurs seances possibles, celle dont la distance cible approche le mieux la
     * distance courue — et a defaut, la plus proche en date. Deux seances le meme jour,
     * un footing et une sortie longue, ne se confondent pas.
     */
    private PlannedSession meilleureCorrespondance(List<PlannedSession> candidates, Activity activite) {
        double distanceKm = activite.distanceKm();
        return candidates.stream()
                .min(Comparator
                        .comparingDouble((PlannedSession s) -> s.getDistanceCibleKm() == null
                                ? Double.MAX_VALUE
                                : Math.abs(s.getDistanceCibleKm().doubleValue() - distanceKm))
                        .thenComparingLong(s -> Math.abs(
                                s.getDate().toEpochDay() - activite.getDateLocale().toEpochDay())))
                .orElseThrow();
    }
}
