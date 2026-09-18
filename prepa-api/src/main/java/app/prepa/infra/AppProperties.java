package app.prepa.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration applicative, prefixe {@code prepa} dans application.yml. */
@ConfigurationProperties(prefix = "prepa")
public record AppProperties(Auth auth, Crypto crypto, Garmin garmin) {

    public record Auth(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Crypto(String key) {}

    /**
     * Acces a Garmin Connect.
     *
     * <p>Les identifiants de consommateur OAuth1 sont ceux de l'application mobile Garmin : ce ne
     * sont pas des secrets propres a ce deploiement, mais des constantes publiques que le service
     * d'echange exige. Ils sont configurables parce que Garmin les fait tourner de loin en loin ;
     * laisses vides, ils sont recuperes au premier passage a l'adresse ou l'ecosysteme les publie.
     *
     * @param pauseEntreAppelsMs respiration entre deux appels au detail d'une seance. Sans elle,
     *     Garmin repond 429 au bout de quelques dizaines de seances.
     * @param joursPremierSync profondeur d'historique reprise pour un athlete jamais synchronise
     */
    public record Garmin(
            String consumerKey,
            String consumerSecret,
            String consumerUrl,
            int pauseEntreAppelsMs,
            int joursPremierSync) {}
}
