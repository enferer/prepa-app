package app.prepa.garmin.client;

import app.prepa.infra.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lecture des seances chez Garmin, une fois la session ouverte.
 *
 * <p>Le resume d'une seance ne contient ni les tours, ni le temps passe en zone cardiaque, ni la
 * meteo : chacun demande son propre appel. C'est ce qui rend un passage long, et ce qui impose
 * deux precautions heritees du worker et conservees telles quelles.
 *
 * <p>La premiere est la respiration entre appels : sans elle, Garmin repond 429 au bout de
 * quelques dizaines de seances, et le passage s'arrete au pire moment — apres avoir consomme son
 * quota, avant d'avoir tout rapatrie.
 *
 * <p>La seconde est le meilleur effort par bloc : une meteo indisponible ne doit pas faire perdre
 * les tours de la meme seance. Chaque bloc manquant devient un trou dans les donnees, jamais un
 * echec de la seance entiere.
 */
@Component
public class GarminClient {

    private static final Logger log = LoggerFactory.getLogger(GarminClient.class);

    private static final String API = "https://connectapi.garmin.com";
    private static final Duration DELAI = Duration.ofSeconds(60);

    /** Garmin plafonne la liste ; au-dela il faut paginer. */
    private static final int TAILLE_PAGE = 100;

    private static final int PAGES_MAX = 20;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final int pauseMs;

    public GarminClient(AppProperties props) {
        this.pauseMs = props.garmin().pauseEntreAppelsMs();
    }

    /** Nom du compte connecte. C'est sur lui que repose le garde-fou d'identite. */
    public String displayName(GarminJetons jetons) {
        JsonNode profil = lire(jetons, "/userprofile-service/socialProfile");
        return profil.path("displayName").asString(null);
    }

    /**
     * Les seances de la periode, resumes seulement.
     *
     * <p>La pagination n'existait pas dans le worker, qui s'en remettait a la bibliotheque. Elle
     * compte pourtant : un premier passage reprend quatre mois d'historique, ce qui depasse
     * largement une page pour un athlete assidu.
     */
    public List<JsonNode> activites(GarminJetons jetons, LocalDate debut, LocalDate fin) {
        List<JsonNode> tout = new ArrayList<>();
        for (int page = 0; page < PAGES_MAX; page++) {
            String chemin = "/activitylist-service/activities/search/activities"
                    + "?startDate=" + debut
                    + "&endDate=" + fin
                    + "&start=" + (page * TAILLE_PAGE)
                    + "&limit=" + TAILLE_PAGE;
            JsonNode lot = lire(jetons, chemin);
            if (!lot.isArray() || lot.isEmpty()) {
                return tout;
            }
            lot.forEach(tout::add);
            if (lot.size() < TAILLE_PAGE) {
                return tout;
            }
            respirer();
        }
        log.warn("Pagination Garmin interrompue à {} pages : période probablement trop large", PAGES_MAX);
        return tout;
    }

    /**
     * Tout ce que le resume ne dit pas, en quatre appels.
     *
     * @return les blocs bruts, sous les memes cles que celles attendues par la normalisation ; un
     *     bloc en echec est simplement absent
     */
    public Map<String, JsonNode> details(GarminJetons jetons, long activityId) {
        Map<String, JsonNode> blocs = new java.util.LinkedHashMap<>();
        ajouterSiPossible(blocs, "splits", jetons, "/activity-service/activity/" + activityId + "/splits");
        ajouterSiPossible(blocs, "zonesFc", jetons, "/activity-service/activity/" + activityId + "/hrTimeInZones");
        ajouterSiPossible(blocs, "meteo", jetons, "/activity-service/activity/" + activityId + "/weather");
        ajouterSiPossible(blocs, "detail", jetons, "/activity-service/activity/" + activityId);
        return blocs;
    }

    private void ajouterSiPossible(
            Map<String, JsonNode> blocs, String nom, GarminJetons jetons, String chemin) {
        try {
            blocs.put(nom, lire(jetons, chemin));
        } catch (GarminException e) {
            log.debug("Bloc {} indisponible pour {} : {}", nom, chemin, e.getMessage());
        } finally {
            respirer();
        }
    }

    private JsonNode lire(GarminJetons jetons, String chemin) {
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(API + chemin))
                    .header("Authorization", "Bearer " + jetons.oauth2Access())
                    .header("Accept", "application/json")
                    .header("NK", "NT")
                    .timeout(DELAI)
                    .GET()
                    .build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() == 401 || reponse.statusCode() == 403) {
                throw GarminException.auth("Garmin a refusé le jeton d'accès (" + reponse.statusCode() + ")");
            }
            if (reponse.statusCode() == 429) {
                // Signale explicitement : c'est le symptome d'une cadence trop soutenue, pas d'une
                // panne, et la reponse est d'augmenter la pause plutot que de reessayer.
                throw GarminException.erreur("Garmin limite le débit (429) : passage interrompu");
            }
            if (reponse.statusCode() >= 400) {
                throw GarminException.erreur("Garmin a répondu " + reponse.statusCode() + " sur " + chemin);
            }
            return json.readTree(reponse.body());
        } catch (GarminException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GarminException.erreur("Appel Garmin interrompu", e);
        } catch (Exception e) {
            throw GarminException.erreur("Appel Garmin en échec sur " + chemin + " : " + e.getMessage(), e);
        }
    }

    private void respirer() {
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
