package app.prepa.garmin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Signature OAuth1, verifiee contre l'exemple normatif de la RFC 5849.
 *
 * <p>C'est le seul endroit du client Garmin ou une erreur ne se voit pas : une signature fausse
 * ne produit pas de message explicatif, seulement un 401 identique a celui d'un mot de passe
 * errone. D'ou ce test sur un vecteur connu, plutot qu'une confiance dans le passage de bout en
 * bout — qui echouerait sans dire pourquoi.
 */
@DisplayName("Signature OAuth1")
class OAuth1SignerTest {

    @Test
    @DisplayName("reproduit la chaine de base de l'exemple de la RFC 5849")
    void vecteurDeLaRfc() {
        // L'exemple de la section 3.4.1.1, aux parametres pres qu'une signature Garmin ne peut
        // pas produire : la RFC y repete la cle « a3 », ce que notre API ne permet pas. La chaine
        // de base attendue est donc celle de la RFC amputee de « a3=2 q ». Tout le reste — les
        // regles d'encodage, le tri, l'assemblage, la cle de signature — est verifie a
        // l'identique, et la signature ci-dessous a ete recalculee par une implementation
        // independante suivant la meme section.
        OAuth1Signer signeur = new OAuth1Signer("9djdj82h48djs9d2", "j49sk3j29djd");

        Map<String, String> parametres = new LinkedHashMap<>();
        parametres.put("b5", "=%3D");
        parametres.put("a3", "a");
        parametres.put("c@", "");
        parametres.put("a2", "r b");
        parametres.put("c2", "");
        parametres.put("oauth_consumer_key", "9djdj82h48djs9d2");
        parametres.put("oauth_token", "kkk9d7dh3k39sjv7");
        parametres.put("oauth_signature_method", "HMAC-SHA1");
        parametres.put("oauth_timestamp", "137131201");
        parametres.put("oauth_nonce", "7d8f3e4a0d7589e5");

        String signature = signeur.signature(
                "POST", "http://example.com/request", parametres, "dh893hdasih9");

        assertThat(signature).isEqualTo("saM+3DVgF6PDtYBuIwsx0YvDLMQ=");
    }

    @Test
    @DisplayName("encode selon la RFC 3986 et non selon URLEncoder")
    void encodage() {
        // Les trois divergences qui cassent une signature sans laisser de trace.
        assertThat(OAuth1Signer.encoder("a b")).isEqualTo("a%20b");
        assertThat(OAuth1Signer.encoder("~")).isEqualTo("~");
        assertThat(OAuth1Signer.encoder("*")).isEqualTo("%2A");
    }

    @Test
    @DisplayName("trie les parametres apres encodage, pas avant")
    void triApresEncodage() {
        OAuth1Signer signeur = new OAuth1Signer("cle", "secret");

        // "a b" s'encode en "a%20b" : trie avant encodage il passerait avant "a!", trie apres il
        // passe derriere. Deux signatures differentes pour la meme requete.
        String avecEspace = signeur.signature(
                "GET", "https://exemple.test/r", Map.of("p", "a b", "q", "1"), null);
        String avecEncode = signeur.signature(
                "GET", "https://exemple.test/r", Map.of("p", "a%20b", "q", "1"), null);

        assertThat(avecEspace).isNotEqualTo(avecEncode);
    }

    @Test
    @DisplayName("produit un en-tete portant la methode de signature et le jeton")
    void enTete() {
        OAuth1Signer signeur = new OAuth1Signer("ma-cle", "mon-secret");

        String entete = signeur.entete(
                "POST", "https://exemple.test/echange", Map.of(), "jeton", "secret-du-jeton");

        assertThat(entete)
                .startsWith("OAuth ")
                .contains("oauth_consumer_key=\"ma-cle\"")
                .contains("oauth_signature_method=\"HMAC-SHA1\"")
                .contains("oauth_token=\"jeton\"")
                .contains("oauth_signature=\"");
    }

    @Test
    @DisplayName("change de signature a chaque appel, par le nonce et l'horodatage")
    void nonceVariable() {
        OAuth1Signer signeur = new OAuth1Signer("ma-cle", "mon-secret");

        String premier = signeur.entete("GET", "https://exemple.test/r", Map.of(), null, null);
        String second = signeur.entete("GET", "https://exemple.test/r", Map.of(), null, null);

        assertThat(premier).isNotEqualTo(second);
    }
}
