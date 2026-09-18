package app.prepa.cycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.prepa.domain.StatutCycle;
import app.prepa.domain.TypeCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Passage quotidien de constat")
class ConstatPlanifieTest {

    private final ConstatService constats = mock(ConstatService.class);
    private final CycleRepository cycles = mock(CycleRepository.class);
    private final ConstatPlanifie planifie = new ConstatPlanifie(constats, cycles);

    @Test
    @DisplayName("traite chaque athlete ayant un cycle actif")
    void tousLesCyclesActifs() {
        UUID premier = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(cycles.findByStatut(StatutCycle.ACTIF)).thenReturn(List.of(cycle(premier), cycle(second)));

        planifie.constaterLesAbsences();

        verify(constats).constaterAbsences(premier);
        verify(constats).constaterAbsences(second);
    }

    @Test
    @DisplayName("un athlete en echec n'empeche pas les autres d'etre traites")
    void unEchecNemportePasLesAutres() {
        UUID casse = UUID.randomUUID();
        UUID sain = UUID.randomUUID();
        when(cycles.findByStatut(StatutCycle.ACTIF)).thenReturn(List.of(cycle(casse), cycle(sain)));
        when(constats.constaterAbsences(casse)).thenThrow(new IllegalStateException("base indisponible"));

        // Le passage est quotidien et automatique : s'il s'arretait au premier athlete en
        // echec, les suivants resteraient sans constat jusqu'au lendemain — sans que rien
        // ne le signale, puisque personne ne l'a declenche.
        assertThatCode(planifie::constaterLesAbsences).doesNotThrowAnyException();
        verify(constats).constaterAbsences(sain);
    }

    @Test
    @DisplayName("ne touche a rien quand aucun cycle n'est actif")
    void aucunCycleActif() {
        when(cycles.findByStatut(StatutCycle.ACTIF)).thenReturn(List.of());

        planifie.constaterLesAbsences();

        verify(constats, org.mockito.Mockito.never()).constaterAbsences(any());
    }

    /** Un vrai cycle : il n'y a rien a simuler dans un objet qui ne fait que porter des champs. */
    private Cycle cycle(UUID athleteId) {
        Cycle cycle = new Cycle(UUID.randomUUID(), athleteId, "cycle", "Cycle", TypeCycle.LIBRE,
                LocalDate.now().minusWeeks(4), LocalDate.now().plusWeeks(4));
        cycle.setStatut(StatutCycle.ACTIF);
        return cycle;
    }
}
