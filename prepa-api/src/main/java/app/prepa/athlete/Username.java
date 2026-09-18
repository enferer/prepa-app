package app.prepa.athlete;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Le nom d'utilisateur : second identifiant de connexion, a cote de l'email.
 *
 * <p>Une seule forme est acceptee en base — minuscules, sans accent, sans espace — pour qu'un
 * nom tape a la main mene toujours au meme compte. Cette classe est le seul endroit qui sait
 * la produire, que le nom soit pose par un admin ou derive du nom affiche a la creation.
 */
public final class Username {

    /** Doit rester en phase avec {@code athletes_username_chk}, migration 012. */
    public static final String FORMAT = "[a-z0-9][a-z0-9._-]{0,29}";

    private static final Pattern VALIDE = Pattern.compile(FORMAT);
    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATEURS = Pattern.compile("[^a-z0-9]+");

    private Username() {}

    /** Ramene une saisie a sa forme canonique, sans juger de sa validite. */
    public static String normaliser(String saisie) {
        return saisie == null ? null : saisie.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean estValide(String username) {
        return username != null && VALIDE.matcher(username).matches();
    }

    /**
     * Derive un nom d'utilisateur du nom affiche : « Thomas D. » donne « thomas-d ».
     *
     * <p>L'unicite n'est pas garantie ici — c'est a l'appelant de la verifier, comme pour un
     * nom pose a la main. Un nom affiche sans aucune lettre latine retombe sur l'identifiant
     * technique, faute de mieux : personne ne se connectera avec, mais la colonne reste tenable.
     */
    public static String depuisNomAffiche(String displayName, UUID id) {
        String sansAccent = DIACRITIQUES
                .matcher(Normalizer.normalize(displayName == null ? "" : displayName, Normalizer.Form.NFD))
                .replaceAll("");
        String slug = SEPARATEURS
                .matcher(sansAccent.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            return "athlete-" + id.toString().substring(0, 8);
        }
        return slug.length() > 30 ? slug.substring(0, 30).replaceAll("-+$", "") : slug;
    }
}
