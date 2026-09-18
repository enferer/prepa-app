package app.prepa.garmin.client;

import java.time.Instant;

/**
 * Les deux jetons qui tiennent une session Garmin ouverte.
 *
 * <p>L'OAuth1 vaut environ un an et ne sert qu'a fabriquer des OAuth2 ; l'OAuth2 vaut une heure
 * et signe les appels d'API. Cette asymetrie est tout l'interet de la paire : tant que l'OAuth1
 * vit, on ne repasse jamais par le mot de passe, donc jamais par la verification en deux etapes.
 *
 * @param oauth1Token jeton long, a conserver precieusement
 * @param oauth1Secret secret associe, necessaire a la signature de l'echange
 * @param oauth2Access porteur des appels d'API, jetable
 * @param expireA fin de validite de l'OAuth2
 */
public record GarminJetons(String oauth1Token, String oauth1Secret, String oauth2Access, Instant expireA) {

    /** Marge avant expiration : un jeton qui meurt en plein passage couterait tout le lot. */
    private static final int MARGE_SECONDES = 120;

    public boolean aUnOauth1() {
        return oauth1Token != null && !oauth1Token.isBlank();
    }

    public boolean oauth2Utilisable() {
        return oauth2Access != null
                && !oauth2Access.isBlank()
                && expireA != null
                && Instant.now().plusSeconds(MARGE_SECONDES).isBefore(expireA);
    }

    public GarminJetons avecOauth2(String acces, long dureeSecondes) {
        return new GarminJetons(
                oauth1Token, oauth1Secret, acces, Instant.now().plusSeconds(dureeSecondes));
    }

    public static GarminJetons vides() {
        return new GarminJetons(null, null, null, null);
    }
}
