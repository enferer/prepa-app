package app.prepa.garmin.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature OAuth 1.0a en HMAC-SHA1, telle que l'exige le service d'echange de jetons Garmin.
 *
 * <p>C'est un protocole mort partout ailleurs, mais les deux appels qui transforment un ticket
 * SSO en jeton d'API le reclament encore. Une signature fausse ne donne pas d'erreur
 * explicative : Garmin repond 401 sans rien dire de plus. D'ou la separation en une classe
 * testable contre les vecteurs de la RFC 5849, plutot qu'en quelques lignes noyees dans le flux
 * d'authentification.
 *
 * <p>Deux pieges classiques, tous deux respectes ici : l'encodage n'est pas celui de
 * {@code URLEncoder} (l'espace devient {@code %20} et non {@code +}, et le tilde reste tel
 * quel), et les parametres sont tries <em>apres</em> encodage, pas avant.
 */
public final class OAuth1Signer {

    private static final SecureRandom ALEA = new SecureRandom();

    private final String consumerKey;
    private final String consumerSecret;

    public OAuth1Signer(String consumerKey, String consumerSecret) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
    }

    /**
     * En-tete {@code Authorization} pour une requete signee.
     *
     * @param methode verbe HTTP, en majuscules
     * @param url URL sans chaine de requete
     * @param parametres parametres de requete, a signer avec le reste
     * @param token jeton OAuth1, nul lors du tout premier echange
     * @param tokenSecret secret associe, nul de meme
     */
    public String entete(
            String methode,
            String url,
            Map<String, String> parametres,
            String token,
            String tokenSecret) {
        Map<String, String> oauth = new TreeMap<>();
        oauth.put("oauth_consumer_key", consumerKey);
        oauth.put("oauth_nonce", nonce());
        oauth.put("oauth_signature_method", "HMAC-SHA1");
        oauth.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        oauth.put("oauth_version", "1.0");
        if (token != null) {
            oauth.put("oauth_token", token);
        }

        Map<String, String> aSigner = new TreeMap<>(parametres);
        aSigner.putAll(oauth);
        oauth.put("oauth_signature", signature(methode, url, aSigner, tokenSecret));

        StringBuilder entete = new StringBuilder("OAuth ");
        boolean premier = true;
        for (Map.Entry<String, String> e : oauth.entrySet()) {
            if (!premier) {
                entete.append(", ");
            }
            entete.append(encoder(e.getKey())).append("=\"").append(encoder(e.getValue())).append('"');
            premier = false;
        }
        return entete.toString();
    }

    String signature(String methode, String url, Map<String, String> parametres, String tokenSecret) {
        // Le tri porte sur les couples deja encodes : "a b" et "a%20b" ne se rangent pas au meme
        // endroit, et Garmin refuse silencieusement si l'ordre differe du sien.
        TreeMap<String, String> encodes = new TreeMap<>();
        parametres.forEach((cle, valeur) -> encodes.put(encoder(cle), encoder(valeur)));

        StringBuilder normalises = new StringBuilder();
        encodes.forEach((cle, valeur) -> {
            if (!normalises.isEmpty()) {
                normalises.append('&');
            }
            normalises.append(cle).append('=').append(valeur);
        });

        String base = methode.toUpperCase(java.util.Locale.ROOT)
                + '&' + encoder(url)
                + '&' + encoder(normalises.toString());
        String cle = encoder(consumerSecret) + '&' + encoder(tokenSecret == null ? "" : tokenSecret);

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return java.util.Base64.getEncoder()
                    .encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw GarminException.erreur("Signature OAuth1 impossible", e);
        }
    }

    /** Percent-encoding de la RFC 3986, que {@code URLEncoder} n'implemente pas tout a fait. */
    static String encoder(String valeur) {
        if (valeur == null) {
            return "";
        }
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String nonce() {
        byte[] octets = new byte[16];
        ALEA.nextBytes(octets);
        return java.util.HexFormat.of().formatHex(octets);
    }
}
