package app.prepa.athlete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * Le nom d'utilisateur, second identifiant de connexion.
 *
 * <p>Il se pose a la creation du compte et ne se change que par l'administration : c'est une
 * porte d'entree, pas une preference d'affichage. Ces tests tiennent les trois regles qui
 * comptent — il est toujours present, il est unique, et il n'est pas servi au vestiaire.
 */
@DisplayName("Nom d'utilisateur")
class NomUtilisateurIntegrationTest extends IntegrationTestBase {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    AthleteRepository athletes;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        athletes.deleteAll();
        Athlete admin = new Athlete(
                UUID.randomUUID(), "patron@example.test", passwordEncoder.encode(MOT_DE_PASSE), "Patron");
        admin.setRole("ADMIN");
        athletes.save(admin);

        String corps = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"patron@example.test\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        jetonAdmin = "Bearer " + objectMapper.readTree(corps).get("accessToken").asString();
    }

    @Test
    @DisplayName("un compte cree sans nom d'utilisateur le tient de son nom affiche")
    void deriveDuNomAffiche() throws Exception {
        creer("""
                {"email":"zoe@example.test","motDePasse":"motdepasse123","displayName":"Zoé Dupré"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("zoe-dupre"));
    }

    @Test
    @DisplayName("un nom d'utilisateur pose est ramene en minuscules")
    void normalise() throws Exception {
        creer("""
                {"email":"max@example.test","username":"MaxT","motDePasse":"motdepasse123",\
                "displayName":"Max"}""")
                .andExpect(status().isBadRequest());

        creer("""
                {"email":"max@example.test","username":"maxt","motDePasse":"motdepasse123",\
                "displayName":"Max"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maxt"));
    }

    @Test
    @DisplayName("deux athletes ne partagent pas un nom d'utilisateur")
    void unicite() throws Exception {
        creer("""
                {"email":"un@example.test","username":"leo","motDePasse":"motdepasse123","displayName":"Leo"}""")
                .andExpect(status().isOk());

        creer("""
                {"email":"deux@example.test","username":"LEO","motDePasse":"motdepasse123","displayName":"Leo B"}""")
                .andExpect(status().isBadRequest());

        creer("""
                {"email":"trois@example.test","motDePasse":"motdepasse123","displayName":"Leo"}""")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("l'administration renomme un compte, et l'ancien nom redevient libre")
    void renommage() throws Exception {
        creer("""
                {"email":"ida@example.test","username":"ida","motDePasse":"motdepasse123","displayName":"Ida"}""")
                .andExpect(status().isOk());
        UUID id = athletes.findByUsernameIgnoreCase("ida").orElseThrow().getId();

        mvc.perform(patch("/api/v1/admin/athletes/" + id)
                        .header("Authorization", jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ida.k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ida.k"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"ida.k\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"ida\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(athletes.existsByUsernameIgnoreCase("ida")).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions creer(String corps) throws Exception {
        return mvc.perform(post("/api/v1/admin/athletes")
                .header("Authorization", jetonAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }
}
