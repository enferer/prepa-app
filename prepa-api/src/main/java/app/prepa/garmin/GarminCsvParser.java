package app.prepa.garmin;

import app.prepa.domain.TypeActivite;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Lecture d'un export CSV Garmin Connect.
 *
 * <p>Une ligne mal formee est signalee et ignoree, jamais fatale : un export de plusieurs
 * centaines d'activites ne doit pas echouer en entier a cause d'une cellule aberrante.
 */
@Component
public class GarminCsvParser {

    /** En-tete de l'export francais vers le champ correspondant. */
    private static final String COL_TYPE = "Type d'activité";

    private static final String COL_DATE = "Date";
    private static final String COL_TITRE = "Titre";
    private static final String COL_DISTANCE = "Distance";
    private static final String COL_DUREE = "Durée";
    private static final String COL_CALORIES = "Calories";
    private static final String COL_FC_MOY = "Fréquence cardiaque moyenne";
    private static final String COL_FC_MAX = "Fréquence cardiaque maximale";
    private static final String COL_ALLURE_MOY = "Allure moyenne";
    private static final String COL_MEILLEURE_ALLURE = "Meilleure allure";
    private static final String COL_GAP = "GAP moyenne";
    private static final String COL_ASCENSION = "Ascension totale";
    private static final String COL_DESCENTE = "Descente totale";
    private static final String COL_CADENCE_MOY = "Cadence de course moyenne";
    private static final String COL_CADENCE_MAX = "Cadence de course maximale";
    private static final String COL_TE_AEROBIE = "TE aérobie";
    private static final String COL_FOULEE = "Longueur moyenne des foulées";
    private static final String COL_OSCILLATION = "Oscillation verticale moyenne";
    private static final String COL_CONTACT_SOL = "Temps de contact moyen avec le sol";
    private static final String COL_PUISSANCE_MOY = "Puissance moyenne";
    private static final String COL_PUISSANCE_MAX = "Puissance max.";
    private static final String COL_TEMPS_DEPLACEMENT = "Temps de déplacement";
    private static final String COL_ALTITUDE_MIN = "Altitude minimale";
    private static final String COL_ALTITUDE_MAX = "Altitude maximale";

    /** Resultat d'un parsing : les lignes exploitables et le detail de ce qui a ete ecarte. */
    public record Resultat(List<GarminDtos.ActiviteBrute> activites, List<Avertissement> avertissements) {}

    public record Avertissement(int ligne, String raison) {}

    public Resultat parser(InputStream flux) throws IOException {
        List<GarminDtos.ActiviteBrute> activites = new ArrayList<>();
        List<Avertissement> avertissements = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8))) {
            String premiereLigne = reader.readLine();
            if (premiereLigne == null) {
                return new Resultat(activites, avertissements);
            }
            premiereLigne = sansBom(premiereLigne);
            char separateur = detecterSeparateur(premiereLigne);
            List<String> entetes = decouper(premiereLigne, separateur);

            String ligne;
            int numero = 1;
            while ((ligne = reader.readLine()) != null) {
                numero++;
                if (ligne.isBlank()) {
                    continue;
                }
                List<String> cellules = decouper(ligne, separateur);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < entetes.size() && i < cellules.size(); i++) {
                    row.put(entetes.get(i).trim(), cellules.get(i));
                }
                try {
                    Optional<GarminDtos.ActiviteBrute> activite = versActivite(row);
                    if (activite.isPresent()) {
                        activites.add(activite.get());
                    } else {
                        avertissements.add(new Avertissement(numero, "Date ou duree illisible, ligne ignoree"));
                    }
                } catch (RuntimeException e) {
                    avertissements.add(new Avertissement(numero, "Ligne illisible : " + e.getMessage()));
                }
            }
        }
        return new Resultat(activites, avertissements);
    }

    private Optional<GarminDtos.ActiviteBrute> versActivite(Map<String, String> row) {
        Optional<LocalDateTime> depart = GarminValues.dateHeure(row.get(COL_DATE));
        Integer dureeSec = GarminValues.duree(row.get(COL_DUREE));
        if (depart.isEmpty() || dureeSec == null) {
            return Optional.empty();
        }

        String libelleType = GarminValues.texte(row.get(COL_TYPE));
        TypeActivite type = GarminActivityTypes.parLibelle(libelleType);
        Double distanceKm = GarminValues.decimal(row.get(COL_DISTANCE));

        GarminDtos.ActiviteBrute activite = new GarminDtos.ActiviteBrute();
        activite.setStartedAtLocal(depart.get());
        activite.setDureeSec(dureeSec);
        activite.setType(type);
        activite.setTypeGarmin(libelleType);
        activite.setTitre(GarminValues.texte(row.get(COL_TITRE)));
        activite.setDistanceM(distanceKm == null ? null : (int) Math.round(distanceKm * 1000));
        activite.setCalories(GarminValues.entier(row.get(COL_CALORIES)));
        activite.setFcMoy(GarminValues.entierCourt(row.get(COL_FC_MOY)));
        activite.setFcMax(GarminValues.entierCourt(row.get(COL_FC_MAX)));
        activite.setAllureMoySecKm(GarminValues.allure(row.get(COL_ALLURE_MOY)));
        activite.setMeilleureAllureSecKm(GarminValues.allure(row.get(COL_MEILLEURE_ALLURE)));
        activite.setGapMoySecKm(GarminValues.allure(row.get(COL_GAP)));
        activite.setDenivelePosM(GarminValues.entier(row.get(COL_ASCENSION)));
        activite.setDeniveleNegM(GarminValues.entier(row.get(COL_DESCENTE)));
        activite.setCadenceMoy(GarminValues.entierCourt(row.get(COL_CADENCE_MOY)));
        activite.setCadenceMax(GarminValues.entierCourt(row.get(COL_CADENCE_MAX)));
        activite.setTeAerobie(GarminValues.decimal(row.get(COL_TE_AEROBIE)));
        activite.setLongueurFouleeM(GarminValues.decimal(row.get(COL_FOULEE)));
        activite.setOscillationVerticale(GarminValues.decimal(row.get(COL_OSCILLATION)));
        activite.setTempsContactSol(GarminValues.entierCourt(row.get(COL_CONTACT_SOL)));
        activite.setPuissanceMoy(GarminValues.entier(row.get(COL_PUISSANCE_MOY)));
        activite.setPuissanceMax(GarminValues.entier(row.get(COL_PUISSANCE_MAX)));
        activite.setDureeMouvementSec(GarminValues.duree(row.get(COL_TEMPS_DEPLACEMENT)));
        activite.setAltitudeMinM(GarminValues.entier(row.get(COL_ALTITUDE_MIN)));
        activite.setAltitudeMaxM(GarminValues.entier(row.get(COL_ALTITUDE_MAX)));
        activite.setSource("CSV_IMPORT");

        // L'allure moyenne peut manquer alors que distance et duree sont la : on la derive.
        if (activite.getAllureMoySecKm() == null && distanceKm != null && distanceKm > 0) {
            activite.setAllureMoySecKm((int) Math.round(dureeSec / distanceKm));
        }
        return Optional.of(activite);
    }

    private static String sansBom(String ligne) {
        return ligne.startsWith("﻿") ? ligne.substring(1) : ligne;
    }

    /** L'export utilise la virgule ou le point-virgule selon la locale du compte. */
    private static char detecterSeparateur(String entete) {
        return entete.chars().filter(c -> c == ';').count() > entete.chars().filter(c -> c == ',').count()
                ? ';'
                : ',';
    }

    /** Decoupage CSV minimal gerant les champs entre guillemets et les guillemets doubles. */
    private static List<String> decouper(String ligne, char separateur) {
        List<String> cellules = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean entreGuillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                if (entreGuillemets && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
                    courant.append('"');
                    i++;
                } else {
                    entreGuillemets = !entreGuillemets;
                }
            } else if (c == separateur && !entreGuillemets) {
                cellules.add(courant.toString());
                courant.setLength(0);
            } else {
                courant.append(c);
            }
        }
        cellules.add(courant.toString());
        return cellules;
    }
}
