package app.prepa.athlete;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleDtos;
import app.prepa.cycle.CycleRepository;
import app.prepa.cycle.PlanService;
import app.prepa.cycle.PlannedSession;
import app.prepa.cycle.TrainingWeek;
import app.prepa.cycle.TrainingWeekRepository;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ce qu'un athlete voit d'un autre, et ce qu'il ne peut pas y faire.
 *
 * <p>La ligne n'est pas « lecture contre ecriture » seulement, mais aussi « l'entrainement
 * contre la personne » : le plan et les sorties se regardent entre athletes, le journal et
 * les blessures non. Deux regles qui se ressemblent assez pour etre confondues plus tard —
 * d'ou ces tests, qui les tiennent separees.
 */
@DisplayName("Lecture croisee entre athletes")
class LectureEntreAthletesIntegrationTest extends IntegrationTestBase {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    AthleteRepository athletes;

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    PlanService planService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private Athlete claire;
    private Athlete bruno;
    private Cycle cycleDeClaire;
    private PlannedSession seanceDeClaire;
    private String jetonDeBruno;

    @BeforeEach
    void preparer() throws Exception {
        cycles.deleteAll();
        athletes.deleteAll();

        claire = creerAthlete("claire@example.test", "Claire");
        bruno = creerAthlete("bruno@example.test", "Bruno");

        LocalDate lundi = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        cycleDeClaire = new Cycle(
                UUID.randomUUID(), claire.getId(), "marathon", "Marathon", TypeCycle.PREPA,
                lundi, lundi.plusWeeks(8));
        cycleDeClaire.setStatut(StatutCycle.ACTIF);
        cycleDeClaire.setCourseNom("Marathon de Nantes");
        cycleDeClaire.setCourseDate(lundi.plusWeeks(8));
        cycleDeClaire.setChronoViseSec(12600);
        cycles.save(cycleDeClaire);

        TrainingWeek semaine = new TrainingWeek(
                UUID.randomUUID(), cycleDeClaire.getId(), (short) 1, lundi, BigDecimal.valueOf(50));
        semaines.save(semaine);

        // Une seance passee et non renseignee : celle que Claire, elle, pourrait trancher.
        seanceDeClaire = planService.ajouter(semaine.getId(), new CycleDtos.SessionInput(
                lundi, (short) 0, TypeSeance.SL, "Sortie longue", null, null, null,
                BigDecimal.valueOf(25), null, null, null, null, null));

        jetonDeBruno = jetonDe("bruno@example.test");
    }

    @Test
    @DisplayName("Bruno voit le plan et les sorties de Claire")
    void lectureDeLEntrainement() throws Exception {
        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/cycles/actif").header("Authorization", jetonDeBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.nom").value("Marathon"));

        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/activities").header("Authorization", jetonDeBruno))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/analysis").header("Authorization", jetonDeBruno))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bruno ne renseigne pas les seances de Claire")
    void ecritureRefusee() throws Exception {
        mvc.perform(patch("/api/v1/sessions/" + seanceDeClaire.getId())
                        .header("Authorization", jetonDeBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"" + StatutSeance.REALISEE + "\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/sessions/" + seanceDeClaire.getId() + "/commentaire")
                        .header("Authorization", jetonDeBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentaireAthlete\":\"bien joué\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/athletes/" + claire.getId() + "/activities/mark-analyzed")
                        .header("Authorization", jetonDeBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le journal, les blessures et le contexte de coach restent a leur athlete")
    void ceQuiResteIntime() throws Exception {
        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/journal").header("Authorization", jetonDeBruno))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/athletes/" + claire.getId() + "/journal")
                        .header("Authorization", jetonDeBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + LocalDate.now() + "\",\"contenu\":\"chez elle\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/injuries").header("Authorization", jetonDeBruno))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/profile").header("Authorization", jetonDeBruno))
                .andExpect(status().isForbidden());

        // Le contexte de coach agrege precisement ce que les lignes ci-dessus protegent.
        mvc.perform(get("/api/v1/athletes/" + claire.getId() + "/coach-context").header("Authorization", jetonDeBruno))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la liste des athletes nomme les autres sans livrer leur email")
    void listeSansEmail() throws Exception {
        String corps = mvc.perform(get("/api/v1/athletes").header("Authorization", jetonDeBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode liste = objectMapper.readTree(corps);
        for (JsonNode athlete : liste) {
            boolean cestBruno = bruno.getId().toString().equals(athlete.get("id").asString());
            org.assertj.core.api.Assertions.assertThat(athlete.get("displayName").asString())
                    .isNotBlank();
            org.assertj.core.api.Assertions.assertThat(
                            athlete.get("email") == null || athlete.get("email").isNull())
                    .as("email visible seulement pour soi")
                    .isEqualTo(!cestBruno);
        }
    }

    private Athlete creerAthlete(String email, String nom) {
        return athletes.save(new Athlete(
                UUID.randomUUID(), email, passwordEncoder.encode(MOT_DE_PASSE), nom));
    }

    private String jetonDe(String email) throws Exception {
        String corps = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(corps).get("accessToken").asString();
    }
}
