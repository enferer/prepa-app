package app.prepa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Parcours d'authentification")
class AuthFlowIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "athlete@example.com";
    private static final String USERNAME = "athlete-test";
    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    AthleteRepository athletes;

    @Autowired
    ServiceKeyService serviceKeys;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private UUID athleteId;

    @BeforeEach
    void creerAthlete() {
        athletes.deleteAll();
        Athlete athlete = new Athlete(UUID.randomUUID(), EMAIL, passwordEncoder.encode(MOT_DE_PASSE), "Athlete Test");
        athletes.save(athlete);
        athleteId = athlete.getId();
    }

    @Test
    @DisplayName("refuse l'acces sans authentification")
    void sansAuth() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("refuse un mot de passe errone sans distinguer email inconnu et mot de passe faux")
    void mauvaisMotDePasse() throws Exception {
        mvc.perform(login(EMAIL, "pas-le-bon"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("BAD_CREDENTIALS"));

        mvc.perform(login("inconnu@example.com", MOT_DE_PASSE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("connecte l'athlete et donne acces a /me")
    void loginPuisMe() throws Exception {
        String accessToken = jetons(EMAIL, MOT_DE_PASSE).get("accessToken").asString();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(athleteId.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.role").value("ATHLETE"));
    }

    @Test
    @DisplayName("accepte la connexion depuis une origine declaree, la refuse depuis une autre")
    void originesDuNavigateur() throws Exception {
        // Un navigateur envoie un en-tete Origin meme quand la page et l'API partagent la
        // meme origine. Une origine absente de la liste fait echouer la connexion avec un
        // 403, alors qu'un appel sans cet en-tete — curl, un test qui l'oublie — reussit :
        // le defaut ne se voit donc qu'une fois dans un navigateur.
        mvc.perform(login(EMAIL, MOT_DE_PASSE).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());

        mvc.perform(login(EMAIL, MOT_DE_PASSE).header("Origin", "http://ailleurs.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("l'email est insensible a la casse")
    void loginCasseEmail() throws Exception {
        mvc.perform(login(EMAIL.toUpperCase(), MOT_DE_PASSE)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("le nom d'utilisateur ouvre la meme session que l'email, quelle qu'en soit la casse")
    void loginParNomUtilisateur() throws Exception {
        String accessToken = jetons(USERNAME, MOT_DE_PASSE).get("accessToken").asString();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(athleteId.toString()));

        mvc.perform(login(USERNAME.toUpperCase(), MOT_DE_PASSE)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un nom d'utilisateur inconnu se refuse comme un email inconnu")
    void loginNomUtilisateurInconnu() throws Exception {
        mvc.perform(login("personne", MOT_DE_PASSE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("un refresh token ne sert qu'une fois : la rotation revoque le precedent")
    void rotationStricte() throws Exception {
        String refresh = jetons(EMAIL, MOT_DE_PASSE).get("refreshToken").asString();

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("le logout revoque le refresh token")
    void logout() throws Exception {
        String refresh = jetons(EMAIL, MOT_DE_PASSE).get("refreshToken").asString();

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("une cle de service nominative authentifie son athlete")
    void cleDeServiceNominative() throws Exception {
        String cle = serviceKeys.creer("test-coach", Set.of("read", "coach"), athleteId).cle();

        mvc.perform(get("/api/v1/me").header("X-Service-Key", cle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(athleteId.toString()));
    }

    @Test
    @DisplayName("une cle revoquee ou inconnue n'authentifie rien")
    void cleInvalide() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Service-Key", "psk_inexistante"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un athlete simple n'atteint pas les endpoints d'administration")
    void athleteNonAdmin() throws Exception {
        String accessToken = jetons(EMAIL, MOT_DE_PASSE).get("accessToken").asString();

        mvc.perform(post("/api/v1/admin/athletes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"autre@example.com","motDePasse":"motdepasse123","displayName":"Autre"}"""))
                .andExpect(status().isForbidden());

        assertThat(athletes.existsByEmailIgnoreCase("autre@example.com")).isFalse();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String identifiant, String motDePasse) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"" + motDePasse + "\"}");
    }

    private JsonNode jetons(String identifiant, String motDePasse) throws Exception {
        String corps = mvc.perform(login(identifiant, motDePasse))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corps);
    }
}
