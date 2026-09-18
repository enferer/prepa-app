package app.prepa.garmin;

import app.prepa.domain.IntensiteTour;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Traduction d'une seance Garmin brute vers la forme d'ingestion.
 *
 * <p>Le pendant de {@link GarminCsvParser} pour les donnees venues de l'API : les deux produisent
 * un {@link GarminDtos.ActiviteBrute}, et le service d'ingestion ne connait que cette forme.
 *
 * <p>La plupart des regles qui suivent ne sont pas des choix d'ecriture mais des corrections de
 * terrain, heritees du synchroniseur precedent et conservees a l'identique : elles ont ete
 * ecrites en constatant ce que Garmin renvoie reellement, pas ce que sa documentation annonce.
 */
@Component
public class GarminNormalizer {

    private static final Logger log = LoggerFactory.getLogger(GarminNormalizer.class);

    /**
     * Bornes de vraisemblance d'une allure, en secondes au kilometre.
     *
     * <p>Une montre restee en marche a l'arret ou un saut de GPS produit des vitesses qui se
     * traduisent par des allures absurdes. Mieux vaut une allure absente qu'une allure fausse :
     * l'absente se voit, la fausse se glisse dans les moyennes.
     */
    private static final int ALLURE_MIN = 100;

    private static final int ALLURE_MAX = 3600;

    /** En dessous des deux, un tour est un artefact : arret de montre, tour fantome de fin. */
    private static final int TOUR_MIN_SECONDES = 10;

    private static final int TOUR_MIN_METRES = 50;

    /**
     * Une seance brute vers sa forme normalisee.
     *
     * @param resume la seance telle que la liste la renvoie
     * @param blocs les details, sous les cles {@code splits}, {@code zonesFc}, {@code meteo} et
     *     {@code detail} ; chacun peut manquer
     * @return {@code null} si la seance n'a pas d'heure de depart exploitable — sans elle, rien
     *     ne permet de la situer ni de la dedupliquer
     */
    public GarminDtos.ActiviteBrute normaliser(JsonNode resume, Map<String, JsonNode> blocs) {
        JsonNode detail = blocs.getOrDefault("detail", null);
        JsonNode summary = detail == null ? null : detail.path("summaryDTO");

        LocalDateTime depart = heureDeDepart(resume, summary);
        if (depart == null) {
            log.warn("Séance Garmin {} ignorée : aucune heure de départ exploitable",
                    resume.path("activityId").asLong(0));
            return null;
        }

        GarminDtos.ActiviteBrute activite = new GarminDtos.ActiviteBrute();
        activite.setGarminActivityId(nombreOuNull(resume.path("activityId")));
        activite.setSource("GARMIN_API");
        activite.setStartedAtLocal(depart);

        String typeKey = resume.path("activityType").path("typeKey").asString(null);
        activite.setTypeGarmin(typeKey);
        activite.setType(GarminActivityTypes.parCle(typeKey));
        activite.setTitre(premierTexte(resume.path("activityName"), noeud(detail, "activityName")));
        activite.setLieu(texteOuNull(noeud(detail, "locationName")));

        // Le detail prime sur le resume : le resume arrondit certaines valeurs, et en omet d'autres.
        Lecteur valeur = new Lecteur(summary, resume);

        activite.setDureeSec(entierOuZero(valeur.lire("duration")));
        activite.setDureeMouvementSec(entier(valeur.lire("movingDuration")));
        activite.setDistanceM(entier(valeur.lire("distance")));
        activite.setAllureMoySecKm(allure(valeur.lire("averageSpeed")));
        activite.setGapMoySecKm(allure(valeur.lire("avgGradeAdjustedSpeed")));
        activite.setFcMoy(petitEntier(valeur.lire("averageHR")));
        activite.setFcMax(petitEntier(valeur.lire("maxHR")));
        activite.setFcMin(petitEntier(noeud(summary, "minHR")));
        activite.setCadenceMoy(petitEntier(
                valeur.lire("averageRunCadence", "averageRunningCadenceInStepsPerMinute")));
        activite.setDenivelePosM(entier(valeur.lire("elevationGain")));
        activite.setDeniveleNegM(entier(valeur.lire("elevationLoss")));
        activite.setAltitudeMinM(entier(valeur.lire("minElevation")));
        activite.setAltitudeMaxM(entier(valeur.lire("maxElevation")));
        activite.setCalories(entier(valeur.lire("calories")));
        activite.setTeAerobie(decimal(valeur.lire("trainingEffect", "aerobicTrainingEffect")));
        activite.setTeAnaerobie(decimal(valeur.lire("anaerobicTrainingEffect")));
        activite.setTeLabel(texteOuNull(noeud(summary, "trainingEffectLabel")));
        activite.setChargeEntrainement(decimal(valeur.lire("activityTrainingLoad")));
        activite.setVo2max(decimal(valeur.lire("vO2MaxValue")));

        activite.setTours(tours(blocs.get("splits")));
        activite.setZonesFc(zonesFc(blocs.get("zonesFc")));
        activite.setMeteo(meteo(blocs.get("meteo")));
        return activite;
    }

    // ------------------------------------------------------------------
    // Blocs de detail
    // ------------------------------------------------------------------

    private List<GarminDtos.TourBrut> tours(JsonNode splits) {
        List<GarminDtos.TourBrut> tours = new ArrayList<>();
        if (splits == null) {
            return tours;
        }
        for (JsonNode lap : splits.path("lapDTOs")) {
            double duree = lap.path("duration").asDouble(0);
            double distance = lap.path("distance").asDouble(0);
            if (duree < TOUR_MIN_SECONDES && distance < TOUR_MIN_METRES) {
                continue;
            }
            GarminDtos.TourBrut tour = new GarminDtos.TourBrut();
            // Index recalcule plutot que repris : Garmin numerote parfois en sautant les tours
            // qu'il considere comme des pauses, et on vient d'en ecarter d'autres.
            tour.setIndex((short) (tours.size() + 1));
            tour.setDistanceM(entier(lap.path("distance")));
            Integer dureeSec = entier(lap.path("duration"));
            tour.setDureeSec(dureeSec == null ? 0 : dureeSec);
            tour.setAllureSecKm(allure(lap.path("averageSpeed")));
            tour.setGapSecKm(allure(lap.path("avgGradeAdjustedSpeed")));
            tour.setFcMoy(petitEntier(lap.path("averageHR")));
            tour.setFcMax(petitEntier(lap.path("maxHR")));
            tour.setCadenceMoy(petitEntier(lap.path("averageRunCadence")));
            tour.setPuissanceMoy(entier(lap.path("averagePower")));
            tour.setDenivelePosM(entier(lap.path("elevationGain")));
            tour.setDeniveleNegM(entier(lap.path("elevationLoss")));
            tour.setIntensite(intensite(lap.path("intensityType").asString(null)));
            tours.add(tour);
        }
        return tours;
    }

    private List<Map<String, Object>> zonesFc(JsonNode zones) {
        List<Map<String, Object>> sortie = new ArrayList<>();
        if (zones == null || !zones.isArray()) {
            return sortie;
        }
        for (JsonNode zone : zones) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("zone", entier(zone.path("zoneNumber")));
            Integer secondes = entier(zone.path("secsInZone"));
            ligne.put("secondes", secondes == null ? 0 : secondes);
            ligne.put("borneBasse", entier(zone.path("zoneLowBoundary")));
            sortie.add(ligne);
        }
        sortie.sort(Comparator.comparingInt(l -> l.get("zone") == null ? 0 : (Integer) l.get("zone")));
        return sortie;
    }

    /** La meteo arrive en unites imperiales quelle que soit la langue du compte. */
    private Map<String, Object> meteo(JsonNode meteo) {
        if (meteo == null || !meteo.isObject() || meteo.isEmpty()) {
            return null;
        }
        Map<String, Object> sortie = new LinkedHashMap<>();
        sortie.put("temperatureC", fahrenheitVersCelsius(meteo.path("temp")));
        sortie.put("ressentiC", fahrenheitVersCelsius(meteo.path("apparentTemp")));
        sortie.put("humidite", entier(meteo.path("relativeHumidity")));
        sortie.put("ventKmh", mphVersKmh(meteo.path("windSpeed")));
        sortie.put("description", texteOuNull(meteo.path("weatherTypeDTO").path("desc")));
        return sortie;
    }

    // ------------------------------------------------------------------
    // Conversions
    // ------------------------------------------------------------------

    /** Vitesse en metres par seconde vers une allure en secondes par kilometre. */
    static Integer allure(JsonNode vitesse) {
        if (vitesse == null || vitesse.isMissingNode() || vitesse.isNull()) {
            return null;
        }
        double ms = vitesse.asDouble(0);
        if (ms <= 0) {
            return null;
        }
        long secondes = Math.round(1000.0 / ms);
        return secondes > ALLURE_MIN && secondes < ALLURE_MAX ? (int) secondes : null;
    }

    static Double fahrenheitVersCelsius(JsonNode f) {
        Double valeur = decimal(f);
        return valeur == null ? null : Math.round((valeur - 32) * 5 / 9 * 10) / 10.0;
    }

    static Double mphVersKmh(JsonNode mph) {
        Double valeur = decimal(mph);
        return valeur == null ? null : Math.round(valeur * 1.609 * 10) / 10.0;
    }

    private static LocalDateTime heureDeDepart(JsonNode resume, JsonNode summary) {
        String brut = premierTexte(resume.path("startTimeLocal"), noeud(summary, "startTimeLocal"));
        if (brut == null) {
            return null;
        }
        String propre = brut.replace(' ', 'T');
        if (propre.length() > 19) {
            propre = propre.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(propre);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static IntensiteTour intensite(String brut) {
        if (brut == null) {
            return IntensiteTour.UNKNOWN;
        }
        try {
            return IntensiteTour.valueOf(brut.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return IntensiteTour.UNKNOWN;
        }
    }

    // ------------------------------------------------------------------
    // Lecture defensive du JSON
    // ------------------------------------------------------------------

    /** Cherche une cle d'abord dans le detail, puis dans le resume. */
    private record Lecteur(JsonNode summary, JsonNode resume) {

        JsonNode lire(String... cles) {
            for (String cle : cles) {
                for (JsonNode source : new JsonNode[] {summary, resume}) {
                    JsonNode trouve = noeud(source, cle);
                    if (trouve != null) {
                        return trouve;
                    }
                }
            }
            return null;
        }
    }

    private static JsonNode noeud(JsonNode source, String cle) {
        if (source == null) {
            return null;
        }
        JsonNode valeur = source.path(cle);
        return valeur.isMissingNode() || valeur.isNull() ? null : valeur;
    }

    private static Integer entier(JsonNode noeud) {
        if (noeud == null || noeud.isMissingNode() || noeud.isNull()) {
            return null;
        }
        return (int) Math.round(noeud.asDouble(0));
    }

    private static int entierOuZero(JsonNode noeud) {
        Integer valeur = entier(noeud);
        return valeur == null ? 0 : valeur;
    }

    private static Short petitEntier(JsonNode noeud) {
        Integer valeur = entier(noeud);
        return valeur == null ? null : valeur.shortValue();
    }

    private static Long nombreOuNull(JsonNode noeud) {
        if (noeud == null || noeud.isMissingNode() || noeud.isNull()) {
            return null;
        }
        long valeur = noeud.asLong(0);
        return valeur == 0 ? null : valeur;
    }

    private static Double decimal(JsonNode noeud) {
        if (noeud == null || noeud.isMissingNode() || noeud.isNull()) {
            return null;
        }
        return noeud.asDouble(0);
    }

    private static String texteOuNull(JsonNode noeud) {
        if (noeud == null || noeud.isMissingNode() || noeud.isNull()) {
            return null;
        }
        String valeur = noeud.asString(null);
        return valeur == null || valeur.isBlank() ? null : valeur;
    }

    private static String premierTexte(JsonNode... noeuds) {
        for (JsonNode noeud : noeuds) {
            String valeur = texteOuNull(noeud);
            if (valeur != null) {
                return valeur;
            }
        }
        return null;
    }
}
