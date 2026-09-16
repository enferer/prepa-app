package app.prepa.garmin;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.domain.TypeActivite;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lecture d'un export CSV Garmin")
class GarminCsvParserTest {

    private final GarminCsvParser parser = new GarminCsvParser();

    @Test
    @DisplayName("lit l'export reel du projet, tous types confondus")
    void exportReel() throws Exception {
        try (InputStream flux = getClass().getResourceAsStream("/fixtures/garmin-extrait.csv")) {
            GarminCsvParser.Resultat resultat = parser.parser(flux);

            assertThat(resultat.avertissements()).isEmpty();
            assertThat(resultat.activites()).hasSize(7);

            GarminDtos.ActiviteBrute premiere = resultat.activites().getFirst();
            assertThat(premiere.getType()).isEqualTo(TypeActivite.TRAIL);
            assertThat(premiere.getStartedAtLocal()).isEqualTo(LocalDateTime.of(2026, 9, 9, 15, 52, 38));
            assertThat(premiere.getDistanceM()).isEqualTo(5050);
        }
    }

    @Test
    @DisplayName("distingue le separateur de milliers du separateur decimal par la nature de la colonne")
    void separateurSelonLaColonne() throws Exception {
        // La meme ecriture a deux sens : "1,443" calories vaut 1443, "21,130" km vaut 21,13.
        // Seule la nature de la colonne — entiere ou decimale — permet de trancher, et le
        // fichier reel melange les deux conventions d'une ligne a l'autre.
        String csv =
                """
                Type d'activité,Date,Distance,Durée,Calories
                Course à pied,2026-02-04 17:31:41,"21,130","01:58:00","1,443"
                Course à pied,2026-09-07 09:53:45,"9.33","01:08:50","685"
                """;
        List<GarminDtos.ActiviteBrute> activites = parser.parser(flux(csv)).activites();

        assertThat(activites.get(0).getDistanceM()).isEqualTo(21130);
        assertThat(activites.get(0).getCalories()).isEqualTo(1443);
        assertThat(activites.get(1).getDistanceM()).isEqualTo(9330);
        assertThat(activites.get(1).getCalories()).isEqualTo(685);
    }

    @Test
    @DisplayName("lit un export a virgule decimale")
    void conventionFrancaise() throws Exception {
        String csv =
                """
                Type d'activité,Date,Distance,Durée,Calories,Fréquence cardiaque moyenne
                Course à pied,12/03/2026 07:30:00,"12,5","01:05:00","780","142"
                """;
        GarminCsvParser.Resultat resultat = parser.parser(flux(csv));

        GarminDtos.ActiviteBrute activite = resultat.activites().getFirst();
        assertThat(activite.getDistanceM()).isEqualTo(12500);
        assertThat(activite.getDureeSec()).isEqualTo(3900);
        assertThat(activite.getCalories()).isEqualTo(780);
        assertThat(activite.getType()).isEqualTo(TypeActivite.RUN);
    }

    @Test
    @DisplayName("compte le tapis comme de la course a pied")
    void tapisEstDeLaCourse() throws Exception {
        String csv =
                """
                Type d'activité,Date,Distance,Durée
                Course sur tapis roulant,2026-03-12 07:30:00,"8.0","00:45:00"
                """;
        GarminDtos.ActiviteBrute activite = parser.parser(flux(csv)).activites().getFirst();

        assertThat(activite.getType()).isEqualTo(TypeActivite.TREADMILL);
        assertThat(activite.getType().estCourseAPied()).isTrue();
    }

    @Test
    @DisplayName("ecarte une ligne illisible sans faire echouer le reste")
    void ligneIllisible() throws Exception {
        String csv =
                """
                Type d'activité,Date,Distance,Durée
                Course à pied,pas-une-date,"8.0","00:45:00"
                Course à pied,2026-03-12 07:30:00,"10.0","01:00:00"
                """;
        GarminCsvParser.Resultat resultat = parser.parser(flux(csv));

        assertThat(resultat.activites()).hasSize(1);
        assertThat(resultat.avertissements()).hasSize(1);
        assertThat(resultat.avertissements().getFirst().ligne()).isEqualTo(2);
    }

    @Test
    @DisplayName("derive l'allure moyenne quand la colonne est absente")
    void allureDerivee() throws Exception {
        String csv =
                """
                Type d'activité,Date,Distance,Durée
                Course à pied,2026-03-12 07:30:00,"10.0","01:00:00"
                """;
        GarminDtos.ActiviteBrute activite = parser.parser(flux(csv)).activites().getFirst();

        assertThat(activite.getAllureMoySecKm()).isEqualTo(360);
    }

    @Test
    @DisplayName("traite les marqueurs de valeur absente comme des trous")
    void valeursAbsentes() throws Exception {
        String csv =
                """
                Type d'activité,Date,Distance,Durée,Fréquence cardiaque moyenne,Puissance moyenne
                Marche à pied,2026-03-12 07:30:00,"6.5","02:10:20",--,...
                """;
        GarminDtos.ActiviteBrute activite = parser.parser(flux(csv)).activites().getFirst();

        assertThat(activite.getFcMoy()).isNull();
        assertThat(activite.getPuissanceMoy()).isNull();
        assertThat(activite.getType()).isEqualTo(TypeActivite.HIKE);
    }

    @Test
    @DisplayName("accepte un point-virgule comme separateur de colonnes")
    void separateurPointVirgule() throws Exception {
        String csv =
                """
                Type d'activité;Date;Distance;Durée
                Course à pied;2026-03-12 07:30:00;10,0;01:00:00
                """;
        assertThat(parser.parser(flux(csv)).activites()).hasSize(1);
    }

    private static InputStream flux(String contenu) {
        return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
    }
}
