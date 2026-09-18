package app.prepa.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.cycle.StructureSeance.RoleBloc;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Le deroule pose par le coach, tel qu'il traverse la base.
 *
 * <p>Un aller-retour en {@code jsonb} n'est pas une formalite : un role mal serialise ou une
 * distance arrondie ne se verrait qu'a l'ecran, longtemps apres. D'ou un Postgres reel.
 */
@DisplayName("Déroulé de séance stocké")
class StructureStockeeTest extends IntegrationTestBase {

    @Autowired
    PlanService planService;

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    PlannedSessionRepository seances;

    @Autowired
    AthleteRepository athletes;

    private TrainingWeek semaine;

    @BeforeEach
    void preparer() {
        cycles.deleteAll();
        athletes.deleteAll();

        Athlete athlete = new Athlete(UUID.randomUUID(), "structure@example.com", "x", "Structure");
        athletes.save(athlete);

        LocalDate lundi = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        Cycle cycle = new Cycle(UUID.randomUUID(), athlete.getId(), "structure", "Cycle",
                TypeCycle.PREPA, lundi, lundi.plusWeeks(6));
        cycle.setStatut(StatutCycle.ACTIF);
        cycle.setCourseNom("Marathon");
        cycle.setCourseDate(lundi.plusWeeks(6));
        cycle.setChronoViseSec(14400);
        cycles.save(cycle);

        semaine = new TrainingWeek(UUID.randomUUID(), cycle.getId(), (short) 1, lundi, BigDecimal.valueOf(40));
        semaines.save(semaine);
    }

    private static CycleDtos.SessionInput entree(List<BlocSeance> structure) {
        return new CycleDtos.SessionInput(
                LocalDate.now(), (short) 0, TypeSeance.SL, "Sortie longue 26 km",
                "SL 26 km : 10 km EF + 2×3 km à AM récup 2 km EF + 8 km EF.",
                null, "EF 6:15 · blocs AM 5:41", new BigDecimal("26.00"), null, null, null, null,
                structure);
    }

    @Test
    @DisplayName("traverse la base sans rien perdre")
    void allerRetour() {
        List<BlocSeance> pose = List.of(
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("10"), null, 375, null),
                new BlocSeance(RoleBloc.EFFORT, 2, new BigDecimal("3"), null, 341, 720),
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("8"), null, 375, null));

        UUID id = planService.ajouter(semaine.getId(), entree(pose)).getId();
        PlannedSession relue = seances.findById(id).orElseThrow();

        assertThat(relue.getStructure()).hasSize(3);
        assertThat(relue.getStructure().get(1).role()).isEqualTo(RoleBloc.EFFORT);
        assertThat(relue.getStructure().get(1).repetitions()).isEqualTo(2);
        assertThat(relue.getStructure().get(1).distanceKm()).isEqualByComparingTo("3");
        assertThat(relue.getStructure().get(1).recupSec()).isEqualTo(720);

        // Et c'est bien lui qui est dessiné, pas la relecture de la phrase.
        CycleDtos.SessionResponse vue = CycleDtos.SessionResponse.from(relue);
        assertThat(vue.structureSaisie()).isTrue();
        assertThat(vue.structure()).hasSize(3);
        assertThat(vue.structure().get(1).libelle()).isEqualTo("2×3 km à 5:41");
    }

    @Test
    @DisplayName("une séance sans déroulé posé se lit dans sa description")
    void sansDeroulePose() {
        UUID id = planService.ajouter(semaine.getId(), entree(null)).getId();

        CycleDtos.SessionResponse vue =
                CycleDtos.SessionResponse.from(seances.findById(id).orElseThrow());

        assertThat(vue.structureSaisie()).isFalse();
        assertThat(vue.structure()).hasSize(3);
    }

    @Test
    @DisplayName("le coach peut poser un déroulé après coup, puis l'effacer")
    void poserPuisEffacer() {
        UUID id = planService.ajouter(semaine.getId(), entree(null)).getId();

        planService.modifierParCoach(id, patch(List.of(
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("26"), null, 375, null))));
        assertThat(CycleDtos.SessionResponse.from(seances.findById(id).orElseThrow()).structure())
                .hasSize(1);

        // Une liste vide rend la séance à sa description plutôt que de la laisser sans déroulé.
        planService.modifierParCoach(id, patch(List.of()));
        CycleDtos.SessionResponse rendue =
                CycleDtos.SessionResponse.from(seances.findById(id).orElseThrow());
        assertThat(rendue.structureSaisie()).isFalse();
        assertThat(rendue.structure()).hasSize(3);
    }

    private static CycleDtos.CoachSessionPatch patch(List<BlocSeance> structure) {
        return new CycleDtos.CoachSessionPatch(
                null, null, null, null, null, null, null, null, null, null, null, structure);
    }
}
