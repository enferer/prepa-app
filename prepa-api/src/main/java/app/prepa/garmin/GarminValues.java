package app.prepa.garmin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion des valeurs de l'export CSV Garmin.
 *
 * <p>Portage direct des helpers de {@code scripts/build_data.py}, qui font foi. Les pieges
 * traites ici sont ceux rencontres sur les exports reels : marqueurs de valeur absente
 * (<em>--</em>, <em>...</em>, tiret cadratin), separateurs decimaux FR ou US selon la locale
 * de l'export, espaces insecables dans les milliers, durees en {@code HH:MM:SS} comme en
 * {@code MM:SS}, et six formats de date possibles.
 */
public final class GarminValues {

    private static final List<String> VIDES = List.of("", "--", "...", "—", "-");
    /** Espaces separateurs de milliers : normal, insecable, fine insecable. */
    private static final Pattern SEPARATEURS_MILLIERS = Pattern.compile("[ \\u00a0\\u202f]");
    private static final Pattern ALLURE = Pattern.compile("(\\d+):(\\d{1,2})");
    private static final Pattern DATE_ISO = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern DATE_FR = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})");

    private static final List<DateTimeFormatter> FORMATS_DATE_HEURE = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

    private GarminValues() {}

    /** Normalise une cellule : vide, marqueur d'absence et espaces deviennent {@code null}. */
    public static String texte(String brut) {
        if (brut == null) {
            return null;
        }
        String v = brut.trim();
        return VIDES.contains(v) ? null : v;
    }
    /**
     * Valeur decimale : distance, training effect, longueur de foulee.
     *
     * <p>Le dernier separateur rencontre est le separateur decimal, les precedents marquent
     * les milliers. {@code "21,130"} vaut ainsi 21,13 km et {@code "1 234,5"} vaut 1234,5.
     */
    public static Double decimal(String brut) {
        String v = prepare(brut);
        if (v == null) {
            return null;
        }
        int dernier = Math.max(v.lastIndexOf(','), v.lastIndexOf('.'));
        if (dernier >= 0) {
            v = v.substring(0, dernier).replace(",", "").replace(".", "") + "." + v.substring(dernier + 1);
        }
        return lire(v);
    }

    /**
     * Valeur entiere : calories, frequence cardiaque, denivele, puissance, nombre de pas.
     *
     * <p>Un champ entier n'a pas de partie decimale : un separateur suivi d'exactement trois
     * chiffres y marque donc les milliers. C'est ce qui distingue {@code "1,443"} calories,
     * qui vaut mille quatre cent quarante-trois, de {@code "21,130"} kilometres, qui vaut
     * vingt-et-un virgule treize — la meme ecriture, deux sens, que seule la nature de la
     * colonne permet de trancher. Confondre les deux faisait lire 1 calorie au lieu de 1443,
     * sans jamais lever d'erreur.
     */
    public static Integer entier(String brut) {
        String v = prepare(brut);
        if (v == null) {
            return null;
        }
        int dernier = Math.max(v.lastIndexOf(','), v.lastIndexOf('.'));
        boolean milliers = dernier >= 0 && v.length() - dernier - 1 == 3;
        Double valeur = milliers ? lire(v.replace(",", "").replace(".", "")) : decimal(v);
        return valeur == null ? null : (int) Math.round(valeur);
    }

    public static Short entierCourt(String brut) {
        Integer i = entier(brut);
        return i == null ? null : i.shortValue();
    }

    /** Retire les espaces separateurs de milliers ; renvoie null si la cellule est vide. */
    private static String prepare(String brut) {
        String v = texte(brut);
        return v == null ? null : SEPARATEURS_MILLIERS.matcher(v).replaceAll("");
    }

    private static Double lire(String v) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Duree {@code HH:MM:SS}, {@code MM:SS} ou {@code SS} en secondes. */
    public static Integer duree(String brut) {
        String v = texte(brut);
        if (v == null) {
            return null;
        }
        String[] parts = v.split(":");
        double[] valeurs = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                valeurs[i] = Double.parseDouble(parts[i].replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        double secondes =
                switch (valeurs.length) {
                    case 3 -> valeurs[0] * 3600 + valeurs[1] * 60 + valeurs[2];
                    case 2 -> valeurs[0] * 60 + valeurs[1];
                    case 1 -> valeurs[0];
                    default -> -1;
                };
        return secondes < 0 ? null : (int) Math.round(secondes);
    }

    /** Allure {@code m:ss} en secondes par kilometre. */
    public static Integer allure(String brut) {
        String v = texte(brut);
        if (v == null) {
            return null;
        }
        Matcher m = ALLURE.matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : null;
    }

    public static boolean booleen(String brut) {
        String v = texte(brut);
        return v != null && List.of("oui", "true", "1", "yes").contains(v.toLowerCase());
    }

    /** Date et heure de depart. L'heure est optionnelle : certains exports ne donnent que le jour. */
    public static Optional<LocalDateTime> dateHeure(String brut) {
        String v = texte(brut);
        if (v == null) {
            return Optional.empty();
        }
        for (DateTimeFormatter format : FORMATS_DATE_HEURE) {
            try {
                return Optional.of(LocalDateTime.parse(v, format));
            } catch (Exception ignored) {
                // format suivant
            }
        }
        return date(v).map(LocalDate::atStartOfDay);
    }

    public static Optional<LocalDate> date(String brut) {
        String v = texte(brut);
        if (v == null) {
            return Optional.empty();
        }
        Matcher iso = DATE_ISO.matcher(v);
        if (iso.find()) {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3))));
        }
        Matcher fr = DATE_FR.matcher(v);
        if (fr.find()) {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(fr.group(3)), Integer.parseInt(fr.group(2)), Integer.parseInt(fr.group(1))));
        }
        return Optional.empty();
    }
}
