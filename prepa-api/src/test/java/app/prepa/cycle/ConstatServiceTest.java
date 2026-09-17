package app.prepa.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.IntegrationTestBase;
import app.prepa.activity.Activity;
import app.prepa.activity.ActivityRepository;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeActivite;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Constat de ce qui a eu lieu")
class ConstatServiceTest extends IntegrationTestBase {

    @Autowired
    ConstatService constats;

    @Autowired
    PlanService planService;

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    PlannedSessionRepository seances;

    @Autowired
    ActivityRepository activities;

    @Autowired
    AthleteRepository athletes;

    private UUID athleteId;
    private Cycle cycle;
    private TrainingWeek semaine;

    @BeforeEach
    void preparer() {
        activities.deleteAll();
        cycles.deleteAll();
        athletes.deleteAll();

        Athlete athlete = new Athlete(UUID.randomUUID(), "constat@example.com", "x", "Constat");
        athletes.save(athlete);
        athleteId = athlete.getId();

        LocalDate lundi = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        cycle = new Cycle(UUID.randomUUID(), athleteId, "cycle", "Cycle", TypeCycle.PREPA,
                lundi.minusWeeks(2), lundi.plusWeeks(6));
        cycle.setStatut(StatutCycle.ACTIF);
        cycle.setCourseDate(lundi.plusWeeks(6));
        cycle.setChronoViseSec(14400);
        cycles.save(cycle);

        semaine = new TrainingWeek(UUID.randomUUID(), cycle.getId(), (short) 1, lundi, BigDecimal.valueOf(40));
        semaines.save(semaine);
    }

    @Test
    @DisplayName("marque une seance realisee des que l'activite arrive")
    void constatALArrivee() {
        PlannedSession seance = planifier(LocalDate.now().minusDays(1), TypeSeance.EF, 10);
        Activity activite = enregistrer(LocalDate.now().minusDays(1), 10_000);

        assertThat(constats.constaterArrivee(activite)).isPresent();

        PlannedSession relue = seances.findById(seance.getId()).orElseThrow();
        assertThat(relue.getStatut()).isEqualTo(StatutSeance.REALISEE);
        assertThat(relue.getActivityId()).isEqualTo(activite.getId());
        assertThat(relue.getRapprochement()).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("choisit la seance dont la distance approche le mieux la sortie")
    void deuxSeancesLeMemeJour() {
        PlannedSession footing = planifier(LocalDate.now().minusDays(1), TypeSeance.EF, 8);
        PlannedSession sortieLongue = planifier(LocalDate.now().minusDays(1), TypeSeance.SL, 24);

        constats.constaterArrivee(enregistrer(LocalDate.now().minusDays(1), 23_500));

        assertThat(seances.findById(sortieLongue.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.REALISEE);
        assertThat(seances.findById(footing.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.A_VENIR);
    }

    @Test
    @DisplayName("ne revient pas sur une seance deja analysee par le coach")
    void neJugePasALaPlaceDuCoach() {
        PlannedSession seance = planifier(LocalDate.now().minusDays(1), TypeSeance.EF, 10);
        seance.setStatut(StatutSeance.ANALYSEE);
        seances.save(seance);

        assertThat(constats.constaterArrivee(enregistrer(LocalDate.now().minusDays(1), 10_000)))
                .isEmpty();
        assertThat(seances.findById(seance.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.ANALYSEE);
    }

    @Test
    @DisplayName("laisse un jour de battement avant de constater une absence")
    void battementAvantLAbsence() {
        PlannedSession hier = planifier(LocalDate.now().minusDays(1), TypeSeance.EF, 10);
        PlannedSession avantHier = planifier(LocalDate.now().minusDays(3), TypeSeance.SL, 20);

        constats.constaterAbsences(athleteId);

        // La montre peut se synchroniser en retard : declarer la seance de la veille non faite
        // serait souvent faux.
        assertThat(seances.findById(hier.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.A_VENIR);
        assertThat(seances.findById(avantHier.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.NON_REALISEE);
    }

    @Test
    @DisplayName("ne constate aucune absence sur le renforcement, que la montre n'enregistre pas")
    void renforcementEpargne() {
        PlannedSession renfo = planifier(LocalDate.now().minusDays(3), TypeSeance.RENFO, null);

        constats.constaterAbsences(athleteId);

        assertThat(seances.findById(renfo.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutSeance.A_VENIR);
    }

    @Test
    @DisplayName("rattrape les activites arrivees avant que le plan n'existe")
    void rattrapage() {
        Activity activite = enregistrer(LocalDate.now().minusDays(2), 12_000);
        PlannedSession seance = planifier(LocalDate.now().minusDays(2), TypeSeance.EF, 12);

        assertThat(constats.rattraper(athleteId, LocalDate.now().minusWeeks(1), LocalDate.now()))
                .isPositive();
        assertThat(seances.findById(seance.getId()).orElseThrow().getActivityId())
                .isEqualTo(activite.getId());
    }

    private PlannedSession planifier(LocalDate date, TypeSeance type, Integer distanceKm) {
        return planService.ajouter(semaine.getId(), new CycleDtos.SessionInput(
                date, (short) 0, type, type.name(), null, null, null,
                distanceKm == null ? null : BigDecimal.valueOf(distanceKm), null,
                type == TypeSeance.RENFO ? "gainage" : null, null, null));
    }

    private Activity enregistrer(LocalDate date, int distanceM) {
        Activity activite = new Activity(
                UUID.randomUUID(), athleteId, UUID.randomUUID().toString(),
                date.atTime(9, 0).toInstant(ZoneOffset.UTC), date, 3600);
        activite.setType(TypeActivite.RUN);
        activite.setDistanceM(distanceM);
        return activities.save(activite);
    }

    @SuppressWarnings("unused")
    private List<PlannedSession> toutesLesSeances() {
        return seances.findByCycleIdOrderByDateAscOrdreAsc(cycle.getId());
    }
}
