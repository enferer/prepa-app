package app.prepa.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La semaine par defaut du rapprochement doit toujours etre celle qui vient de s'achever,
 * y compris quand le point est fait le dimanche meme — jour de la sortie longue, donc le
 * moment le plus probable pour lancer /prepa-update.
 */
class AnalysisControllerTest {

    @Test
    @DisplayName("lance un dimanche, vise la semaine qui s'acheve ce jour-la")
    void dimancheMemeJour() {
        // Semaine du lundi 14/09 au dimanche 20/09 2026.
        assertThat(AnalysisController.semaineEcoulee(LocalDate.of(2026, 9, 20)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("lance un lundi, vise la semaine qui vient de s'achever la veille")
    void lundiLendemain() {
        assertThat(AnalysisController.semaineEcoulee(LocalDate.of(2026, 9, 21)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("lance en milieu de semaine, vise toujours la semaine precedente complete")
    void milieuDeSemaine() {
        assertThat(AnalysisController.semaineEcoulee(LocalDate.of(2026, 9, 23)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("lance le samedi, la semaine en cours n'est pas encore terminee")
    void samediPasEncoreTerminee() {
        assertThat(AnalysisController.semaineEcoulee(LocalDate.of(2026, 9, 26)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }
}
