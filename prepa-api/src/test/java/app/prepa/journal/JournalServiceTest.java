package app.prepa.journal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Detection d'une gene dans le journal")
class JournalServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Le genou tire un peu depuis mardi",
        "Petite douleur au mollet en fin de sortie",
        "Grosse gêne rotulienne, j'ai écourté",
        "Mal aux ischios au réveil",
        "Sensation de pincement sous le pied",
        "J'ai eu mal a la cheville",
    })
    @DisplayName("repere une gene formulee avec les mots de tous les jours")
    void detecte(String texte) {
        assertThat(JournalService.mentionneUneDouleur(texte)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Très bonne semaine, jambes légères",
        "Sortie longue tranquille, rien à signaler",
        "Fatigué mais content de la séance",
        "Il faisait chaud, j'ai levé le pied",
    })
    @DisplayName("ne voit pas de gene la ou il n'y en a pas")
    void neDetectePas(String texte) {
        assertThat(JournalService.mentionneUneDouleur(texte)).isFalse();
    }

    @Test
    @DisplayName("ne se laisse pas tromper par la casse ni les accents manquants")
    void casseEtAccents() {
        assertThat(JournalService.mentionneUneDouleur("GENE au tendon")).isTrue();
        assertThat(JournalService.mentionneUneDouleur("gêne au tendon")).isTrue();
        assertThat(JournalService.mentionneUneDouleur("Douleur")).isTrue();
    }

    @Test
    @DisplayName("un texte vide ne signale rien")
    void texteVide() {
        assertThat(JournalService.mentionneUneDouleur(null)).isFalse();
        assertThat(JournalService.mentionneUneDouleur("")).isFalse();
    }
}
