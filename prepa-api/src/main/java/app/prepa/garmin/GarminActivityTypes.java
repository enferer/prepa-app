package app.prepa.garmin;

import app.prepa.domain.TypeActivite;
import java.util.Locale;
import java.util.Map;

/**
 * Correspondance entre les libelles Garmin et notre typologie d'activites.
 *
 * <p>Table unique, volontairement : la version precedente avait deux listes divergentes — le
 * synchroniseur ecrivait « Course sur tapis roulant » quand l'analyse cherchait « Course sur
 * tapis », si bien que les seances sur tapis disparaissaient silencieusement des syntheses.
 * Tout passe desormais par ici, dans les deux sens.
 */
public final class GarminActivityTypes {

    /** Cle technique de l'API Garmin ({@code typeKey}) vers notre type. */
    private static final Map<String, TypeActivite> PAR_CLE = Map.ofEntries(
            Map.entry("running", TypeActivite.RUN),
            Map.entry("track_running", TypeActivite.RUN),
            Map.entry("obstacle_run", TypeActivite.RUN),
            Map.entry("street_running", TypeActivite.RUN),
            Map.entry("trail_running", TypeActivite.TRAIL),
            Map.entry("ultra_run", TypeActivite.TRAIL),
            Map.entry("treadmill_running", TypeActivite.TREADMILL),
            Map.entry("indoor_running", TypeActivite.TREADMILL),
            Map.entry("virtual_run", TypeActivite.TREADMILL),
            Map.entry("cycling", TypeActivite.BIKE),
            Map.entry("road_biking", TypeActivite.BIKE),
            Map.entry("mountain_biking", TypeActivite.BIKE),
            Map.entry("gravel_cycling", TypeActivite.BIKE),
            Map.entry("indoor_cycling", TypeActivite.BIKE),
            Map.entry("virtual_ride", TypeActivite.BIKE),
            Map.entry("lap_swimming", TypeActivite.SWIM),
            Map.entry("open_water_swimming", TypeActivite.SWIM),
            Map.entry("strength_training", TypeActivite.STRENGTH),
            Map.entry("indoor_cardio", TypeActivite.STRENGTH),
            Map.entry("yoga", TypeActivite.STRENGTH),
            Map.entry("pilates", TypeActivite.STRENGTH),
            Map.entry("hiking", TypeActivite.HIKE),
            Map.entry("walking", TypeActivite.HIKE));

    /** Libelle francais de l'export CSV vers notre type, en minuscules. */
    private static final Map<String, TypeActivite> PAR_LIBELLE = Map.ofEntries(
            Map.entry("course à pied", TypeActivite.RUN),
            Map.entry("course a pied", TypeActivite.RUN),
            Map.entry("course à pied sur piste", TypeActivite.RUN),
            Map.entry("course d'obstacles", TypeActivite.RUN),
            Map.entry("trail", TypeActivite.TRAIL),
            Map.entry("course sur tapis roulant", TypeActivite.TREADMILL),
            Map.entry("course sur tapis", TypeActivite.TREADMILL),
            Map.entry("course à pied en intérieur", TypeActivite.TREADMILL),
            Map.entry("cyclisme", TypeActivite.BIKE),
            Map.entry("vélo de route", TypeActivite.BIKE),
            Map.entry("vtt", TypeActivite.BIKE),
            Map.entry("vélo gravel", TypeActivite.BIKE),
            Map.entry("vélo en intérieur", TypeActivite.BIKE),
            Map.entry("sortie virtuelle", TypeActivite.BIKE),
            Map.entry("vélo elliptique", TypeActivite.BIKE),
            Map.entry("natation en piscine", TypeActivite.SWIM),
            Map.entry("natation en eau libre", TypeActivite.SWIM),
            Map.entry("musculation", TypeActivite.STRENGTH),
            Map.entry("cardio en intérieur", TypeActivite.STRENGTH),
            Map.entry("yoga", TypeActivite.STRENGTH),
            Map.entry("pilates", TypeActivite.STRENGTH),
            Map.entry("randonnée", TypeActivite.HIKE),
            Map.entry("marche à pied", TypeActivite.HIKE));

    private GarminActivityTypes() {}

    public static TypeActivite parCle(String typeKey) {
        if (typeKey == null) {
            return TypeActivite.OTHER;
        }
        return PAR_CLE.getOrDefault(typeKey.toLowerCase(Locale.ROOT), TypeActivite.OTHER);
    }

    public static TypeActivite parLibelle(String libelle) {
        if (libelle == null) {
            return TypeActivite.OTHER;
        }
        return PAR_LIBELLE.getOrDefault(libelle.trim().toLowerCase(Locale.ROOT), TypeActivite.OTHER);
    }
}
