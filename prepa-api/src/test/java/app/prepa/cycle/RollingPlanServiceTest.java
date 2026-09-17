package app.prepa.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.domain.BlocEntrainement;
import app.prepa.domain.LigneDirectrice;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeSeance;
import app.prepa.domain.TypeCycle;
import app.prepa.infra.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Plan glissant des cycles libres")
class RollingPlanServiceTest extends IntegrationTestBase {

    @Autowired
    RollingPlanService planGlissant;

    @Autowired
    CycleService cycleService;

    @Autowired
    PlanService planService;

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    AthleteRepository athletes;

    private UUID athleteId;

    @BeforeEach
    void preparer() {
        cycles.deleteAll();
        athletes.deleteAll();
        Athlete athlete = new Athlete(UUID.randomUUID(), "libre@example.com", "x", "Libre");
        athletes.save(athlete);
        athleteId = athlete.getId();
    }

    @Test
    @DisplayName("place une decharge toutes les quatre semaines, a volume reduit")
    void dechargePeriodique() {
        List<RollingPlanService.CibleHebdo> trame =
                planGlissant.squelette(cycleLibre(LigneDirectrice.TRAIL_DENIVELE, (short) 8), 40);

        assertThat(trame.get(3).decharge()).isTrue();
        assertThat(trame.get(3).bloc()).isEqualTo(BlocEntrainement.DECHARGE);
        assertThat(trame.get(7).decharge()).isTrue();
        assertThat(trame.get(0).decharge()).isFalse();

        // La decharge retire un quart de la charge de la semaine pleine precedente.
        double pleine = trame.get(2).volumeCibleKm().doubleValue();
        assertThat(trame.get(3).volumeCibleKm().doubleValue()).isEqualTo(round(pleine * 0.75));
    }

    @Test
    @DisplayName("ne fait pas repartir la charge en arriere apres une decharge")
    void progressionDepuisLaDerniereSemainePleine() {
        List<RollingPlanService.CibleHebdo> trame =
                planGlissant.squelette(cycleLibre(LigneDirectrice.TRAIL_DENIVELE, (short) 6), 40);

        // La semaine qui suit la decharge reprend au-dessus de la derniere semaine pleine,
        // et non au-dessus de la decharge.
        assertThat(trame.get(4).volumeCibleKm().doubleValue())
                .isGreaterThan(trame.get(2).volumeCibleKm().doubleValue());
    }

    @Test
    @DisplayName("ne monte pas le volume quand l'intention est de tenir la charge")
    void maintienDeCharge() {
        List<RollingPlanService.CibleHebdo> trame =
                planGlissant.squelette(cycleLibre(LigneDirectrice.MAINTIEN_CHARGE, (short) 6), 45);

        assertThat(trame.get(0).volumeCibleKm()).isEqualByComparingTo(BigDecimal.valueOf(45.0));
        assertThat(trame.get(4).volumeCibleKm()).isEqualByComparingTo(BigDecimal.valueOf(45.0));
    }

    @Test
    @DisplayName("limite la hausse a dix pour cent d'une semaine sur l'autre")
    void progressionPlafonnee() {
        List<RollingPlanService.CibleHebdo> trame =
                planGlissant.squelette(cycleLibre(LigneDirectrice.ENDURANCE_FONDAMENTALE, (short) 3), 40);

        double premiere = trame.get(0).volumeCibleKm().doubleValue();
        double seconde = trame.get(1).volumeCibleKm().doubleValue();
        assertThat(seconde / premiere).isLessThanOrEqualTo(1.10001);
    }

    @Test
    @DisplayName("donne une cible de denivele aux cycles orientes trail, et a eux seuls")
    void cibleDeDenivele() {
        assertThat(planGlissant.squelette(cycleLibre(LigneDirectrice.TRAIL_DENIVELE, (short) 3), 40)
                        .getFirst()
                        .deniveleCibleM())
                .isNotNull();
        assertThat(planGlissant.squelette(cycleLibre(LigneDirectrice.VO2MAX, (short) 3), 40)
                        .getFirst()
                        .deniveleCibleM())
                .isNull();
    }

    @Test
    @DisplayName("ne detaille que les prochaines semaines")
    void fenetreDeDetail() {
        List<RollingPlanService.CibleHebdo> trame =
                planGlissant.squelette(cycleLibre(LigneDirectrice.MAINTIEN_CHARGE, (short) 12), 45);

        assertThat(trame.stream().filter(RollingPlanService.CibleHebdo::detaillee))
                .hasSize(RollingPlanService.SEMAINES_DETAILLEES);
        assertThat(trame.get(RollingPlanService.SEMAINES_DETAILLEES).detaillee()).isFalse();
    }

    @Test
    @DisplayName("avance la fenetre de detail d'une semaine a chaque point")
    void ouvertureDeLaSemaineSuivante() {
        Cycle cycle = poserPlan(LigneDirectrice.MAINTIEN_CHARGE, (short) 8);

        assertThat(planGlissant.ouvrirSemaineSuivante(cycle.getId())).isPresent();

        long detaillees = semaines.findByCycleIdOrderByNumeroAsc(cycle.getId()).stream()
                .filter(TrainingWeek::isDetaillee)
                .count();
        assertThat(detaillees).isEqualTo(RollingPlanService.SEMAINES_DETAILLEES + 1);
    }

    @Test
    @DisplayName("repousse l'horizon sans toucher aux semaines deja posees")
    void prolongation() {
        Cycle cycle = poserPlan(LigneDirectrice.MAINTIEN_CHARGE, (short) 8);

        List<TrainingWeek> ajoutees = planGlissant.prolonger(cycle.getId(), 4);

        assertThat(ajoutees).hasSize(4);
        assertThat(semaines.findByCycleIdOrderByNumeroAsc(cycle.getId())).hasSize(12);
        assertThat(cycles.findById(cycle.getId()).orElseThrow().getHorizonSemaines()).isEqualTo((short) 12);
        assertThat(ajoutees).allMatch(s -> !s.isDetaillee());
    }

    @Test
    @DisplayName("refuse de prolonger une preparation : elle a une date de course")
    void prepaNonProlongeable() {
        Cycle prepa = new Cycle(
                UUID.randomUUID(), athleteId, "marathon", "Marathon", TypeCycle.PREPA,
                LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 11));
        prepa.setCourseDate(LocalDate.of(2027, 4, 11));
        prepa.setChronoViseSec(14400);
        cycles.save(prepa);

        assertThatThrownBy(() -> planGlissant.prolonger(prepa.getId(), 4))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de course");
    }

    @Test
    @DisplayName("mesure l'assiduite sur les seules seances tranchees")
    void assiduiteSurSeancesResolues() {
        Cycle cycle = poserPlan(LigneDirectrice.MAINTIEN_CHARGE, (short) 4);

        RollingPlanService.BilanCycle bilan = planGlissant.bilan(cycle.getId());

        // Aucune seance n'est encore validee ni manquee : l'assiduite n'a pas de sens.
        assertThat(bilan.assiduitePct()).isNull();
        assertThat(bilan.nbSemaines()).isEqualTo(4);
    }

    @Test
    @DisplayName("exclut le renforcement de l'assiduite")
    void assiduiteHorsRenforcement() {
        Cycle cycle = poserPlan(LigneDirectrice.MAINTIEN_CHARGE, (short) 4);
        TrainingWeek premiere = semaines.findByCycleIdOrderByNumeroAsc(cycle.getId()).getFirst();

        // Deux sorties tenues, et un renforcement marque manque parce que la montre ne
        // l'enregistre pas : l'assiduite doit rester a cent pour cent.
        ajouter(premiere, TypeSeance.EF, StatutSeance.VALIDEE, null);
        ajouter(premiere, TypeSeance.SL, StatutSeance.VALIDEE, null);
        ajouter(premiere, TypeSeance.RENFO, StatutSeance.MANQUEE, "gainage");

        assertThat(planGlissant.bilan(cycle.getId()).assiduitePct()).isEqualTo(100);
    }

    private void ajouter(TrainingWeek semaine, TypeSeance type, StatutSeance statut, String focus) {
        planService.ajouter(semaine.getId(), new CycleDtos.SessionInput(
                semaine.getDateDebut(), (short) 0, type, type.name(), null, statut, null, null, null,
                focus, null, null));
    }

    private Cycle cycleLibre(LigneDirectrice ligne, short horizon) {
        Cycle cycle = new Cycle(
                UUID.randomUUID(), athleteId, "libre-" + ligne.name().toLowerCase() + "-" + horizon,
                "Cycle libre", TypeCycle.LIBRE,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2).plusWeeks(horizon));
        cycle.setLigneDirectrice("Intention de test");
        cycle.setLigneDirectriceType(ligne);
        cycle.setHorizonSemaines(horizon);
        return cycles.save(cycle);
    }

    /** Cree un cycle libre et pose la trame en base, sans seances. */
    private Cycle poserPlan(LigneDirectrice ligne, short horizon) {
        Cycle cycle = cycleLibre(ligne, horizon);
        cycle.setStatut(StatutCycle.ACTIF);
        cycles.save(cycle);

        List<CycleDtos.WeekInput> entrees = planGlissant.squelette(cycle, 45).stream()
                .map(c -> new CycleDtos.WeekInput(
                        c.numero(), c.dateDebut(), c.bloc(), c.volumeCibleKm(), c.nbQualiteCible(),
                        c.deniveleCibleM(), c.detaillee(), null, List.of()))
                .toList();
        planService.remplacerPlan(cycle.getId(), new CycleDtos.PutPlanRequest(entrees));
        return cycle;
    }

    private static double round(double valeur) {
        return Math.round(valeur * 10) / 10.0;
    }
}
