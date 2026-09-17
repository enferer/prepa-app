package app.prepa.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Les origines autorisees viennent de la configuration, pas du code.
 *
 * <p>Une installation servie en clair sur un nom de machine ne ressemble ni au poste de
 * developpement, ni a un site en HTTPS. Quand la liste etait figee sur ces deux cas, une
 * telle installation refusait toute connexion depuis un navigateur avec un 403 — sans que
 * rien ne le signale ailleurs, puisque {@code curl} n'envoie pas d'en-tete {@code Origin}
 * et reussissait.
 *
 * <p>Cette classe demande donc un contexte a elle, avec une origine qu'aucune valeur par
 * defaut ne couvre : elle echoue si la propriete cesse d'etre lue.
 */
@TestPropertySource(properties = "prepa.web.origines-autorisees=http://machine.test:8080")
@DisplayName("Origines autorisees configurees")
class OriginesConfigureesIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "origine@example.com";
    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    AthleteRepository athletes;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void creerAthlete() {
        athletes.deleteAll();
        athletes.save(new Athlete(UUID.randomUUID(), EMAIL, passwordEncoder.encode(MOT_DE_PASSE), "Athlete Test"));
    }

    @Test
    @DisplayName("accepte l'origine declaree en configuration")
    void origineDeclaree() throws Exception {
        mvc.perform(login().header("Origin", "http://machine.test:8080")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("refuse une origine absente de la configuration, fut-elle le defaut du developpement")
    void origineAbsente() throws Exception {
        mvc.perform(login().header("Origin", "http://localhost:5173")).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login() {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}");
    }
}
