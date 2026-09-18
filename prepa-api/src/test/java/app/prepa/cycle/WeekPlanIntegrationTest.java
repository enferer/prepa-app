package app.prepa.cycle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.AthleteRepository;
import app.prepa.athlete.Athlete;
import app.prepa.auth.ServiceKeyService;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * Les deux volumes d'une semaine, et la route qui permet de rediscuter le premier.
 *
 * <p>Ce qu'une semaine <em>vise</em> et ce que ses seances <em>totalisent</em> sont deux
 * chiffres distincts. Les confondre — en recalculant la cible a chaque retouche — rendrait
 * l'ecart invisible au moment ou il compte : celui ou le coach a rallonge une sortie et doit
 * decider s'il assume la charge en plus ou s'il la reprend ailleurs.
 */
@DisplayName("Cibles d'une semaine du plan")
class WeekPlanIntegrationTest extends IntegrationTestBase {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired
    PlanService planService;

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    AthleteRepository athletes;

    @Autowired
    ServiceKeyService serviceKeys;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private TrainingWeek semaine;
    private String cleCoach;

    @BeforeEach
    void preparer() {
        cycles.deleteAll();
        athletes.deleteAll();

        Athlete athlete = new Athlete(
                UUID.randomUUID(), "semaine@example.test", passwordEncoder.encode(MOT_DE_PASSE), "Semaine");
        athletes.save(athlete);

        LocalDate lundi = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        Cycle cycle = new Cycle(UUID.randomUUID(), athlete.getId(), "cycle", "Cycle", TypeCycle.PREPA,
                lundi, lundi.plusWeeks(6));
        cycle.setStatut(StatutCycle.ACTIF);
        cycle.setCourseNom("Marathon");
        cycle.setCourseDate(lundi.plusWeeks(6));
        cycle.setChronoViseSec(14400);
        cycles.save(cycle);

        semaine = new TrainingWeek(UUID.randomUUID(), cycle.getId(), (short) 1, lundi, BigDecimal.valueOf(56));
        semaines.save(semaine);

        cleCoach = serviceKeys.creer("coach", Set.of("read", "coach"), null).cle();
    }

    @Test
    @DisplayName("distingue ce que la semaine vise de ce que ses seances totalisent")
    void deuxVolumes() throws Exception {
        planifier(TypeSeance.EF, 9);
        planifier(TypeSeance.AM, 10);
        planifier(TypeSeance.EF, 8);
        planifier(TypeSeance.SL, 30);

        mvc.perform(get("/api/v1/weeks/" + semaine.getId()).header("X-Service-Key", cleCoach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeCibleKm").value(56.0))
                .andExpect(jsonPath("$.volumePlanifieKm").value(57.0));
    }

    @Test
    @DisplayName("sort du compte une seance annulee, garde une seance deplacee")
    void annuleeEtDeplacee() throws Exception {
        planifier(TypeSeance.EF, 9);
        PlannedSession annulee = planifier(TypeSeance.EF, 8);
        PlannedSession deplacee = planifier(TypeSeance.SL, 30);

        planService.modifierParCoach(annulee.getId(), patchStatut(StatutSeance.ANNULEE));
        planService.modifierParCoach(deplacee.getId(), patchStatut(StatutSeance.DEPLACEE));

        // Annuler, c'est retirer du plan ; deplacer, c'est le meme travail un autre jour.
        mvc.perform(get("/api/v1/weeks/" + semaine.getId()).header("X-Service-Key", cleCoach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumePlanifieKm").value(39.0));
    }

    @Test
    @DisplayName("le renfo n'entre pas dans le volume")
    void renfoHorsVolume() throws Exception {
        planifier(TypeSeance.EF, 9);
        planifier(TypeSeance.RENFO, null);

        mvc.perform(get("/api/v1/weeks/" + semaine.getId()).header("X-Service-Key", cleCoach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumePlanifieKm").value(9.0));
    }

    @Test
    @DisplayName("le coach redit ce que la semaine vise, sans rejouer tout le plan")
    void coachAjusteLaCible() throws Exception {
        planifier(TypeSeance.SL, 30);

        mvc.perform(patch("/api/v1/weeks/" + semaine.getId())
                        .header("X-Service-Key", cleCoach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumeCibleKm\":57,\"note\":\"Pic de volume\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeCibleKm").value(57.0))
                .andExpect(jsonPath("$.note").value("Pic de volume"));

        // Un champ absent est un champ inchange : le bloc et les seances ne bougent pas.
        mvc.perform(get("/api/v1/weeks/" + semaine.getId()).header("X-Service-Key", cleCoach))
                .andExpect(jsonPath("$.volumeCibleKm").value(57.0))
                .andExpect(jsonPath("$.volumePlanifieKm").value(30.0))
                .andExpect(jsonPath("$.seances.length()").value(1));
    }

    @Test
    @DisplayName("modifier une seance ne reecrit pas silencieusement la cible de la semaine")
    void laCibleNeSuitPasLesSeances() throws Exception {
        PlannedSession sortieLongue = planifier(TypeSeance.SL, 28);

        mvc.perform(patch("/api/v1/sessions/" + sortieLongue.getId())
                        .header("X-Service-Key", cleCoach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceCibleKm\":30}"))
                .andExpect(status().isOk());

        // La cible reste l'intention posee : c'est justement ce qui rend l'ecart lisible.
        mvc.perform(get("/api/v1/weeks/" + semaine.getId()).header("X-Service-Key", cleCoach))
                .andExpect(jsonPath("$.volumeCibleKm").value(56.0))
                .andExpect(jsonPath("$.volumePlanifieKm").value(30.0));
    }

    @Test
    @DisplayName("refuse a l'athlete de redessiner la charge de sa semaine")
    void athleteNeChangePasLaCharge() throws Exception {
        mvc.perform(patch("/api/v1/weeks/" + semaine.getId())
                        .header("Authorization", "Bearer " + jetonAthlete())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumeCibleKm\":80}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuse une cible de volume negative")
    void cibleNegative() throws Exception {
        mvc.perform(patch("/api/v1/weeks/" + semaine.getId())
                        .header("X-Service-Key", cleCoach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumeCibleKm\":-10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("repond 404 sur une semaine inconnue, et non 500")
    void semaineInconnue() throws Exception {
        mvc.perform(get("/api/v1/weeks/" + UUID.randomUUID()).header("X-Service-Key", cleCoach))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("repond 404 sur une route qui n'existe pas, et non 500")
    void routeInconnue() throws Exception {
        // Un 500 signifie « le serveur est en panne », donc « reessaie ». Sur une route absente
        // — un client reste sur une ancienne version, une URL mal tapee — c'est un contresens,
        // et cela noircissait les journaux d'« Erreur non geree ».
        mvc.perform(get("/api/v1/route-qui-nexiste-pas").header("X-Service-Key", cleCoach))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private PlannedSession planifier(TypeSeance type, Integer distanceKm) {
        return planService.ajouter(semaine.getId(), new CycleDtos.SessionInput(
                semaine.getDateDebut(), (short) 0, type, type.name(), null, null, null,
                distanceKm == null ? null : BigDecimal.valueOf(distanceKm), null,
                type == TypeSeance.RENFO ? "gainage" : null, null, null, null));
    }

    private static CycleDtos.CoachSessionPatch patchStatut(StatutSeance statut) {
        return new CycleDtos.CoachSessionPatch(
                statut, null, null, null, null, null, null, null, null, null, null, null);
    }

    private String jetonAthlete() throws Exception {
        String corps = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"semaine@example.test\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corps).get("accessToken").asString();
    }
}
