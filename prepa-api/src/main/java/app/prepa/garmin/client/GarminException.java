package app.prepa.garmin.client;

import app.prepa.garmin.StatutSync;

/**
 * Echec sur un athlete, portant deja son diagnostic.
 *
 * <p>Le worker Python devinait le statut en cherchant des morceaux de phrase dans le message
 * d'erreur (« mauvais compte », « connexion Garmin refusee »). Un message reformule cassait
 * silencieusement le classement, et un {@code IDENTITE_KO} redevenait une erreur banale qu'on
 * reessayait. Le statut est desormais pose a l'endroit ou l'on sait ce qui s'est passe.
 */
public class GarminException extends RuntimeException {

    private final StatutSync statut;

    public GarminException(StatutSync statut, String message) {
        super(message);
        this.statut = statut;
    }

    public GarminException(StatutSync statut, String message, Throwable cause) {
        super(message, cause);
        this.statut = statut;
    }

    public static GarminException auth(String message) {
        return new GarminException(StatutSync.AUTH_ERROR, message);
    }

    public static GarminException auth(String message, Throwable cause) {
        return new GarminException(StatutSync.AUTH_ERROR, message, cause);
    }

    public static GarminException identite(String message) {
        return new GarminException(StatutSync.IDENTITE_KO, message);
    }

    public static GarminException mfa(String message) {
        return new GarminException(StatutSync.MFA_REQUISE, message);
    }

    public static GarminException erreur(String message) {
        return new GarminException(StatutSync.ERREUR, message);
    }

    public static GarminException erreur(String message, Throwable cause) {
        return new GarminException(StatutSync.ERREUR, message, cause);
    }

    public StatutSync statut() {
        return statut;
    }
}
