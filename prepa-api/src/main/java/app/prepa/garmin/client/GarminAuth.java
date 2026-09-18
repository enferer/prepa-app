package app.prepa.garmin.client;

import app.prepa.infra.AppProperties;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ouverture d'une session Garmin : portail d'authentification, puis echange de jetons.
 *
 * <p>Garmin n'expose pas d'API d'authentification. Le chemin reel est celui du navigateur, en
 * quatre temps : on charge le formulaire de connexion pour en tirer un jeton anti-rejeu, on le
 * poste avec les identifiants pour obtenir un ticket, on echange ce ticket contre un jeton OAuth1
 * — sur un service qui reclame encore une signature HMAC-SHA1 — puis cet OAuth1 contre le jeton
 * OAuth2 qui signera les appels d'API.
 *
 * <p>C'est le point fragile de tout l'edifice, et il est isole ici pour cette raison : Garmin
 * modifie ce parcours sans preavis, et le jour ou il changera, c'est ce fichier seul qu'il
 * faudra reprendre.
 *
 * <p>Le chemin complet n'est emprunte qu'en l'absence de jeton OAuth1 valide. Le cas courant
 * n'execute que le dernier echange, qui ne demande pas le mot de passe — c'est ce qui evite de
 * declencher la verification en deux etapes a chaque passage.
 */
@Component
public class GarminAuth {

    private static final Logger log = LoggerFactory.getLogger(GarminAuth.class);

    private static final String SSO = "https://sso.garmin.com/sso";
    private static final String SSO_EMBED = SSO + "/embed";
    private static final String CONNECT_API = "https://connectapi.garmin.com";
    private static final String PREAUTORISE = CONNECT_API + "/oauth-service/oauth/preauthorized";
    private static final String ECHANGE = CONNECT_API + "/oauth-service/oauth/exchange/user/2.0";

    /** Le service d'echange refuse un agent qui ne se presente pas comme l'application mobile. */
    private static final String UA_MOBILE = "com.garmin.android.apps.connectmobile";

    private static final String UA_NAVIGATEUR =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"(.+?)\"");
    private static final Pattern TITRE = Pattern.compile("<title>(.+?)</title>");
    private static final Pattern TICKET = Pattern.compile("embed\\?ticket=([^\"]+)\"");

    private static final Duration DELAI = Duration.ofSeconds(30);

    private final ObjectMapper json = new ObjectMapper();
    private final AppProperties.Garmin config;

    /**
     * Resolu une fois pour toutes au premier besoin.
     *
     * <p>Il serait tentant de le resoudre au demarrage, mais cela ferait dependre l'API entiere
     * d'un service tiers pour se lever — alors que la synchronisation n'est qu'une de ses
     * fonctions, et pas celle dont depend l'affichage d'un plan d'entrainement.
     */
    private volatile OAuth1Signer signeur;

    public GarminAuth(AppProperties props) {
        this.config = props.garmin();
    }

    private OAuth1Signer signeur() {
        OAuth1Signer connu = signeur;
        if (connu != null) {
            return connu;
        }
        synchronized (this) {
            if (signeur == null) {
                signeur = construireSigneur();
            }
            return signeur;
        }
    }

    private OAuth1Signer construireSigneur() {
        if (config.consumerKey() != null && !config.consumerKey().isBlank()) {
            return new OAuth1Signer(config.consumerKey(), config.consumerSecret());
        }
        log.info("Identifiants de consommateur Garmin non configurés, récupération depuis {}",
                config.consumerUrl());
        JsonNode noeud = json.readTree(appeler(HttpRequest.newBuilder()
                .uri(URI.create(config.consumerUrl()))
                .timeout(DELAI)
                .GET()));
        String cle = noeud.path("consumer_key").asString(null);
        String secret = noeud.path("consumer_secret").asString(null);
        if (cle == null || secret == null) {
            throw GarminException.erreur(
                    "Identifiants de consommateur Garmin introuvables : renseigner prepa.garmin.consumer-key");
        }
        return new OAuth1Signer(cle, secret);
    }

    /**
     * Rend une session utilisable, au moindre cout.
     *
     * @param jetons ce qu'on a deja en reserve, eventuellement vide
     * @throws GarminMfaEnAttente si Garmin reclame un code, avec la session mise de cote
     */
    public GarminJetons ouvrir(String email, String motDePasse, GarminJetons jetons) {
        if (jetons != null && jetons.oauth2Utilisable()) {
            return jetons;
        }
        if (jetons != null && jetons.aUnOauth1()) {
            try {
                return echangerOauth2(jetons);
            } catch (GarminException e) {
                // Un OAuth1 revoque ou expire se soigne en repassant par le mot de passe. On le
                // signale : c'est la difference entre une panne passagere et un compte a reconnecter.
                log.info("Jeton Garmin de longue duree inutilisable, reprise par mot de passe : {}",
                        e.getMessage());
            }
        }
        return connexionComplete(email, motDePasse);
    }

    /** Reprend une connexion laissee en suspens par une demande de code. */
    public GarminJetons validerMfa(String contexte, String code) {
        Session session = Session.reprendre(contexte, json);
        String ticket = posterCodeMfa(session, code);
        return echangerOauth2(echangerOauth1(ticket));
    }

    // ------------------------------------------------------------------
    // Le parcours du navigateur
    // ------------------------------------------------------------------

    private GarminJetons connexionComplete(String email, String motDePasse) {
        Session session = Session.neuve();

        // Le premier appel ne sert qu'a recolter les cookies : sans eux, le formulaire suivant
        // repond bien mais son jeton anti-rejeu est refuse au moment du POST.
        session.get(SSO_EMBED, parametresEmbed());

        Map<String, String> params = parametresConnexion();
        String urlFormulaire = SSO + "/signin";
        String formulaire = session.get(urlFormulaire, params);
        String csrf = extraire(CSRF, formulaire, "jeton anti-rejeu du formulaire de connexion");

        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("username", email);
        corps.put("password", motDePasse);
        corps.put("embed", "true");
        corps.put("_csrf", csrf);
        String reponse = session.postFormulaire(
                urlFormulaire, params, corps, urlAvecParametres(urlFormulaire, params));

        String titre = premierGroupe(TITRE, reponse);
        if (titre != null && titre.contains("MFA")) {
            session.memoriser(urlFormulaire, params);
            throw new GarminMfaEnAttente(session.serialiser(json));
        }

        Matcher ticket = TICKET.matcher(reponse);
        if (!ticket.find()) {
            // Garmin ne renvoie pas de code d'erreur exploitable : un mot de passe faux et un
            // compte verrouille donnent la meme page. On ne peut qu'attribuer l'echec aux
            // identifiants, ce qui est vrai dans l'immense majorite des cas.
            throw GarminException.auth(
                    "Garmin a refusé la connexion (page reçue : " + (titre == null ? "inconnue" : titre) + ")");
        }
        return echangerOauth2(echangerOauth1(ticket.group(1)));
    }

    private String posterCodeMfa(Session session, String code) {
        Map<String, String> params = parametresConnexion();
        String url = SSO + "/verifyMFA/loginEnterMfaCode";
        String formulaire = session.get(url, params);
        String csrf = extraire(CSRF, formulaire, "jeton anti-rejeu du formulaire de vérification");

        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("mfa-code", code);
        corps.put("embed", "true");
        corps.put("_csrf", csrf);
        corps.put("fromPage", "setupEnterMfaCode");
        String reponse = session.postFormulaire(url, params, corps, urlAvecParametres(url, params));

        Matcher ticket = TICKET.matcher(reponse);
        if (!ticket.find()) {
            throw GarminException.auth("Code de vérification refusé par Garmin");
        }
        return ticket.group(1);
    }

    // ------------------------------------------------------------------
    // Les echanges de jetons
    // ------------------------------------------------------------------

    private GarminJetons echangerOauth1(String ticket) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ticket", ticket);
        params.put("login-url", SSO_EMBED);
        params.put("accepts-mfa-tokens", "true");

        String entete = signeur().entete("GET", PREAUTORISE, params, null, null);

        String reponse = appeler(HttpRequest.newBuilder()
                .uri(URI.create(urlAvecParametres(PREAUTORISE, params)))
                .header("Authorization", entete)
                .header("User-Agent", UA_MOBILE)
                .timeout(DELAI)
                .GET());

        // Reponse en chaine de requete, pas en JSON : oauth_token=...&oauth_token_secret=...
        Map<String, String> champs = lireChaineDeRequete(reponse);
        String token = champs.get("oauth_token");
        String secret = champs.get("oauth_token_secret");
        if (token == null || secret == null) {
            throw GarminException.auth("Échange du ticket Garmin sans jeton en retour");
        }
        return new GarminJetons(token, secret, null, null);
    }

    private GarminJetons echangerOauth2(GarminJetons jetons) {
        String entete = signeur().entete(
                "POST", ECHANGE, Map.of(), jetons.oauth1Token(), jetons.oauth1Secret());

        String reponse = appeler(HttpRequest.newBuilder()
                .uri(URI.create(ECHANGE))
                .header("Authorization", entete)
                .header("User-Agent", UA_MOBILE)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(DELAI)
                .POST(HttpRequest.BodyPublishers.noBody()));

        JsonNode noeud = json.readTree(reponse);
        String acces = noeud.path("access_token").asString(null);
        if (acces == null) {
            throw GarminException.auth("Échange du jeton Garmin sans jeton d'accès en retour");
        }
        return jetons.avecOauth2(acces, noeud.path("expires_in").asLong(3600L));
    }

    private static String appeler(HttpRequest.Builder requete) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(DELAI)
                    .build();
            HttpResponse<String> reponse = client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() >= 400) {
                throw GarminException.auth(
                        "Garmin a répondu " + reponse.statusCode() + " à un échange de jeton");
            }
            return reponse.body();
        } catch (GarminException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GarminException.erreur("Échange de jeton interrompu", e);
        } catch (Exception e) {
            throw GarminException.erreur("Garmin injoignable : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Parametres et petits outils
    // ------------------------------------------------------------------

    private static Map<String, String> parametresEmbed() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", "gauth-widget");
        params.put("embedWidget", "true");
        params.put("gauthHost", SSO);
        return params;
    }

    /**
     * Le portail se comporte differemment selon l'application qui l'appelle ; ces parametres le
     * font se comporter comme le widget embarque, seul mode qui rende un ticket exploitable.
     */
    private static Map<String, String> parametresConnexion() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", "gauth-widget");
        params.put("embedWidget", "true");
        params.put("gauthHost", SSO_EMBED);
        params.put("service", SSO_EMBED);
        params.put("source", SSO_EMBED);
        params.put("redirectAfterAccountLoginUrl", SSO_EMBED);
        params.put("redirectAfterAccountCreationUrl", SSO_EMBED);
        return params;
    }

    static String urlAvecParametres(String url, Map<String, String> params) {
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder sortie = new StringBuilder(url).append('?');
        boolean premier = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!premier) {
                sortie.append('&');
            }
            sortie.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            premier = false;
        }
        return sortie.toString();
    }

    private static Map<String, String> lireChaineDeRequete(String chaine) {
        Map<String, String> champs = new LinkedHashMap<>();
        for (String couple : chaine.split("&")) {
            int egal = couple.indexOf('=');
            if (egal > 0) {
                champs.put(
                        java.net.URLDecoder.decode(couple.substring(0, egal), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(couple.substring(egal + 1), StandardCharsets.UTF_8));
            }
        }
        return champs;
    }

    private static String extraire(Pattern motif, String texte, String quoi) {
        String trouve = premierGroupe(motif, texte);
        if (trouve == null) {
            throw GarminException.auth("Page de connexion Garmin inattendue : " + quoi + " introuvable");
        }
        return trouve;
    }

    private static String premierGroupe(Pattern motif, String texte) {
        Matcher m = motif.matcher(texte);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Une session de navigateur : un client HTTP et ses cookies.
     *
     * <p>Elle sait se serialiser, parce qu'une demande de code coupe le parcours en deux appels
     * separes par plusieurs minutes et par un aller-retour avec un humain. Les cookies doivent
     * survivre a cet intervalle, faute de quoi Garmin ne reconnait pas la tentative de connexion
     * a laquelle le code repond.
     */
    private static final class Session {

        private final CookieManager cookies;
        private final HttpClient client;
        private String urlMemorisee;
        private Map<String, String> paramsMemorises = Map.of();

        private Session(CookieManager cookies) {
            this.cookies = cookies;
            this.client = HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(DELAI)
                    .build();
        }

        static Session neuve() {
            return new Session(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }

        static Session reprendre(String contexte, ObjectMapper json) {
            CookieManager gestionnaire = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            JsonNode noeud = json.readTree(contexte);
            for (JsonNode biscuit : noeud.path("cookies")) {
                HttpCookie cookie = new HttpCookie(
                        biscuit.path("nom").asString(), biscuit.path("valeur").asString());
                cookie.setDomain(biscuit.path("domaine").asString("sso.garmin.com"));
                cookie.setPath(biscuit.path("chemin").asString("/"));
                cookie.setVersion(0);
                gestionnaire.getCookieStore().add(URI.create("https://sso.garmin.com"), cookie);
            }
            return new Session(gestionnaire);
        }

        void memoriser(String url, Map<String, String> params) {
            this.urlMemorisee = url;
            this.paramsMemorises = params;
        }

        String serialiser(ObjectMapper json) {
            List<Map<String, String>> biscuits = new ArrayList<>();
            for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
                biscuits.add(Map.of(
                        "nom", cookie.getName(),
                        "valeur", cookie.getValue(),
                        "domaine", cookie.getDomain() == null ? "sso.garmin.com" : cookie.getDomain(),
                        "chemin", cookie.getPath() == null ? "/" : cookie.getPath()));
            }
            return json.writeValueAsString(Map.of(
                    "cookies", biscuits,
                    "url", urlMemorisee == null ? "" : urlMemorisee,
                    "params", paramsMemorises));
        }

        String get(String url, Map<String, String> params) {
            return envoyer(HttpRequest.newBuilder()
                    .uri(URI.create(urlAvecParametres(url, params)))
                    .header("User-Agent", UA_NAVIGATEUR)
                    .timeout(DELAI)
                    .GET());
        }

        String postFormulaire(
                String url, Map<String, String> params, Map<String, String> corps, String referer) {
            return envoyer(HttpRequest.newBuilder()
                    .uri(URI.create(urlAvecParametres(url, params)))
                    .header("User-Agent", UA_NAVIGATEUR)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", referer)
                    .timeout(DELAI)
                    .POST(HttpRequest.BodyPublishers.ofString(encoderFormulaire(corps))));
        }

        private String envoyer(HttpRequest.Builder requete) {
            try {
                HttpResponse<String> reponse =
                        client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
                if (reponse.statusCode() >= 400) {
                    throw GarminException.auth(
                            "Le portail Garmin a répondu " + reponse.statusCode());
                }
                return reponse.body();
            } catch (GarminException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw GarminException.erreur("Connexion Garmin interrompue", e);
            } catch (Exception e) {
                throw GarminException.erreur("Portail Garmin injoignable : " + e.getMessage(), e);
            }
        }

        private static String encoderFormulaire(Map<String, String> corps) {
            StringBuilder sortie = new StringBuilder();
            corps.forEach((cle, valeur) -> {
                if (!sortie.isEmpty()) {
                    sortie.append('&');
                }
                sortie.append(URLEncoder.encode(cle, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(valeur, StandardCharsets.UTF_8));
            });
            return sortie.toString();
        }
    }
}
