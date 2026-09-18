package app.prepa.garmin;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.domain.IntensiteTour;
import app.prepa.domain.TypeActivite;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduction d'une seance Garmin brute.
 *
 * <p>Les fixtures reproduisent la forme reelle des reponses de Garmin, y compris ses
 * irregularites : unites imperiales pour la meteo, zones cardiaques dans le desordre, tour
 * fantome de quatre secondes, intensite inconnue, vitesse nulle. Ce sont ces cas-la qui ont
 * motive les regles testees ici ; un jeu de donnees propre ne prouverait rien.
 */
@DisplayName("Normalisation d'une séance Garmin")
class GarminNormalizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final GarminNormalizer normalizer = new GarminNormalizer();

    @Test
    @DisplayName("porte l'identité, le type et l'heure de départ de la séance")
    void identite() {
        GarminDtos.ActiviteBrute activite = normaliser();

        assertThat(activite.getGarminActivityId()).isEqualTo(18234567890L);
        assertThat(activite.getSource()).isEqualTo("GARMIN_API");
        assertThat(activite.getType()).isEqualTo(TypeActivite.RUN);
        assertThat(activite.getTypeGarmin()).isEqualTo("running");
        assertThat(activite.getTitre()).isEqualTo("Seuil 3x2000");
        assertThat(activite.getLieu()).isEqualTo("Parc de la Tête d'Or");
        assertThat(activite.getStartedAtLocal()).isEqualTo(LocalDateTime.of(2026, 9, 15, 18, 42, 11));
    }

    @Test
    @DisplayName("préfère le détail au résumé, qui arrondit et omet")
    void detailPrioritaire() {
        GarminDtos.ActiviteBrute activite = normaliser();

        // Ces trois-la n'existent que dans le detail : sans la priorite, elles seraient perdues.
        assertThat(activite.getDureeMouvementSec()).isEqualTo(3560);
        assertThat(activite.getFcMin()).isEqualTo((short) 98);
        assertThat(activite.getGapMoySecKm()).isEqualTo(294);
        assertThat(activite.getTeLabel()).isEqualTo("TEMPO");
    }

    @Test
    @DisplayName("convertit une vitesse en allure, et arrondit les grandeurs entières")
    void conversions() {
        GarminDtos.ActiviteBrute activite = normaliser();

        // 3,3421 m/s = 1000 / 3,3421 = 299,2 s/km
        assertThat(activite.getAllureMoySecKm()).isEqualTo(299);
        assertThat(activite.getDistanceM()).isEqualTo(12043);
        assertThat(activite.getDureeSec()).isEqualTo(3612);
        assertThat(activite.getFcMoy()).isEqualTo((short) 156);
        assertThat(activite.getDenivelePosM()).isEqualTo(129);
        assertThat(activite.getVo2max()).isEqualTo(54.0);
    }

    @Test
    @DisplayName("écarte les tours fantômes et renumérote ceux qui restent")
    void toursFantomes() {
        GarminDtos.ActiviteBrute activite = normaliser();

        // Quatre tours en entree, dont un de 4 s et 12 m : un artefact d'arret de montre.
        assertThat(activite.getTours()).hasSize(3);
        assertThat(activite.getTours()).extracting(GarminDtos.TourBrut::getIndex)
                .containsExactly((short) 1, (short) 2, (short) 3);
        assertThat(activite.getTours().getFirst().getIntensite()).isEqualTo(IntensiteTour.WARMUP);
        assertThat(activite.getTours().get(1).getAllureSecKm()).isEqualTo(210);
    }

    @Test
    @DisplayName("laisse l'allure vide plutôt que fausse quand la vitesse est aberrante")
    void allureAberrante() {
        GarminDtos.ActiviteBrute activite = normaliser();

        // Le dernier tour a une vitesse nulle — montre a l'arret. Une allure absente se voit ;
        // une allure fausse se glisserait dans les moyennes.
        GarminDtos.TourBrut dernier = activite.getTours().getLast();
        assertThat(dernier.getAllureSecKm()).isNull();
        assertThat(dernier.getFcMoy()).isEqualTo((short) 121);
    }

    @Test
    @DisplayName("retient UNKNOWN pour une intensité que Garmin n'a jamais annoncée")
    void intensiteInconnue() {
        GarminDtos.ActiviteBrute activite = normaliser();

        assertThat(activite.getTours().getLast().getIntensite()).isEqualTo(IntensiteTour.UNKNOWN);
    }

    @Test
    @DisplayName("remet les zones cardiaques dans l'ordre")
    void zonesTriees() {
        GarminDtos.ActiviteBrute activite = normaliser();

        assertThat(activite.getZonesFc()).hasSize(3);
        assertThat(activite.getZonesFc()).extracting(z -> z.get("zone")).containsExactly(1, 2, 3);
        assertThat(activite.getZonesFc().getFirst().get("secondes")).isEqualTo(310);
    }

    @Test
    @DisplayName("ramène la météo en unités métriques")
    void meteoMetrique() {
        Map<String, Object> meteo = normaliser().getMeteo();

        // Garmin repond en Fahrenheit et en milles par heure quelle que soit la langue du compte.
        assertThat(meteo.get("temperatureC")).isEqualTo(20.0);
        assertThat(meteo.get("ressentiC")).isEqualTo(22.0);
        assertThat(meteo.get("ventKmh")).isEqualTo(14.5);
        assertThat(meteo.get("humidite")).isEqualTo(62);
        assertThat(meteo.get("description")).isEqualTo("Partiellement nuageux");
    }

    @Test
    @DisplayName("survit à des blocs de détail absents")
    void blocsAbsents() {
        // Une meteo indisponible ne doit pas faire perdre la seance : le client remonte les blocs
        // qu'il a pu obtenir, et la normalisation compose avec ce qui manque.
        GarminDtos.ActiviteBrute activite = normalizer.normaliser(lire("resume.json"), Map.of());

        assertThat(activite).isNotNull();
        assertThat(activite.getDistanceM()).isEqualTo(12043);
        assertThat(activite.getTours()).isEmpty();
        assertThat(activite.getZonesFc()).isEmpty();
        assertThat(activite.getMeteo()).isNull();
    }

    @Test
    @DisplayName("écarte une séance sans heure de départ exploitable")
    void sansHeureDeDepart() {
        // Sans elle, rien ne permet de situer la seance ni de la dedupliquer.
        JsonNode sansDepart = JSON.readTree("{\"activityId\": 1, \"duration\": 600}");

        assertThat(normalizer.normaliser(sansDepart, Map.of())).isNull();
    }

    private GarminDtos.ActiviteBrute normaliser() {
        Map<String, JsonNode> blocs = new LinkedHashMap<>();
        blocs.put("splits", lire("splits.json"));
        blocs.put("zonesFc", lire("zones.json"));
        blocs.put("meteo", lire("meteo.json"));
        blocs.put("detail", lire("detail.json"));
        return normalizer.normaliser(lire("resume.json"), blocs);
    }

    private static JsonNode lire(String fichier) {
        try (InputStream flux =
                GarminNormalizerTest.class.getResourceAsStream("/fixtures/garmin/" + fichier)) {
            return JSON.readTree(flux);
        } catch (Exception e) {
            throw new IllegalStateException("Fixture " + fichier + " illisible", e);
        }
    }
}
