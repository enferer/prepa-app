package app.prepa.garmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.prepa.IntegrationTestBase;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.auth.ServiceKeyService;
import app.prepa.garmin.client.GarminAuth;
import app.prepa.garmin.client.GarminClient;
import app.prepa.garmin.client.GarminJetons;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Acces a l'exploitation de la synchronisation.
 *
 * <p>Ces routes vivent sous {@code /api/v1/admin} pour une raison precise, verifiee ici : un
 * administrateur connecte porte un jeton sans aucun scope, et ne franchirait donc jamais les
 * gardes des routes reservees au coach. Le pendant, lui aussi verifie, est qu'une cle de service
 * ne les atteint pas — elle dispose de sa propre route par athlete.
 */
@DisplayName("Exploitation de la synchronisation Garmin")
class GarminAdminControllerTest extends IntegrationTestBase {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @MockitoBean
    GarminAuth auth;

    @MockitoBean
    GarminClient garmin;

    @Autowired
    AthleteRepository athletes;

    @Autowired
    GarminCredentialsRepository credentials;

    @Autowired
    GarminSyncRunRepository runs;

    @Autowired
    GarminSyncService sync;

    @Autowired
    ServiceKeyService serviceKeys;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private UUID athleteId;

    @BeforeEach
    void preparer() {
        runs.deleteAll();
        credentials.deleteAll();
        athletes.deleteAll();

        Athlete admin = new Athlete(UUID.randomUUID(), "admin@example.test",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin");
        admin.setRole("ADMIN");
        athletes.save(admin);

        Athlete athlete = new Athlete(UUID.randomUUID(), "coureur@example.test",
                passwordEncoder.encode(MOT_DE_PASSE), "Coureur");
        athletes.save(athlete);
        athleteId = athlete.getId();
        sync.enregistrerIdentifiants(athleteId, "coureur@example.test", "secret-garmin");

        when(auth.ouvrir(any(), any(), any()))
                .thenReturn(new GarminJetons("t", "s", "a", Instant.now().plusSeconds(3600)));
        when(garmin.displayName(any())).thenReturn("compte-garmin");
        when(garmin.activites(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("montre l'état des comptes reliés à un administrateur")
    void etatPourAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/garmin/etat").header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCours").value(false))
                .andExpect(jsonPath("$.comptes.length()").value(1))
                .andExpect(jsonPath("$.comptes[0].athlete").value("Coureur"));
    }

    @Test
    @DisplayName("refuse l'écran à un athlète ordinaire")
    void refuseUnAthlete() throws Exception {
        mvc.perform(get("/api/v1/admin/garmin/etat")
                        .header("Authorization", "Bearer " + jeton("coureur@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuse l'écran à une clé de service, même dotée du droit d'ingestion")
    void refuseUneCleDeService() throws Exception {
        // Elle n'en a pas besoin : POST /athletes/{id}/sync lui est ouvert, et c'est par la que
        // passent les skills.
        String cle = serviceKeys.creer("worker", Set.of("read", "coach", "ingest"), null).cle();

        mvc.perform(get("/api/v1/admin/garmin/etat").header("X-Service-Key", cle))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuse l'écran sans authentification")
    void refuseSansAuth() throws Exception {
        mvc.perform(get("/api/v1/admin/garmin/etat")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("accepte un lancement manuel et le trace au nom de son auteur")
    void lancementManuel() throws Exception {
        mvc.perform(post("/api/v1/admin/garmin/athletes/" + athleteId + "/sync")
                        .header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isAccepted());

        // L'execution est asynchrone : on attend que la trace apparaisse plutot que de supposer
        // qu'elle est deja la.
        GarminSyncRun trace = attendreUneTrace();
        org.assertj.core.api.Assertions.assertThat(trace.getDeclencheur())
                .isEqualTo(DeclencheurSync.MANUEL);
        org.assertj.core.api.Assertions.assertThat(trace.getDemandePar()).isEqualTo("Admin");
    }

    @Test
    @DisplayName("refuse de lancer sur un athlète sans compte Garmin, plutôt que d'accepter en vain")
    void lancementSansCompte() throws Exception {
        Athlete sansCompte = new Athlete(UUID.randomUUID(), "sans@example.test",
                passwordEncoder.encode(MOT_DE_PASSE), "Sans compte");
        athletes.save(sansCompte);

        mvc.perform(post("/api/v1/admin/garmin/athletes/" + sansCompte.getId() + "/sync")
                        .header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("l'historique se filtre par athlète")
    void historiqueFiltre() throws Exception {
        mvc.perform(post("/api/v1/admin/garmin/athletes/" + athleteId + "/sync")
                        .header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isAccepted());
        attendreUneTrace();

        mvc.perform(get("/api/v1/admin/garmin/runs?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].athlete").value("Coureur"))
                .andExpect(jsonPath("$[0].statut").value("OK"));

        UUID autre = UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/garmin/runs?athleteId=" + autre)
                        .header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("les anciennes routes du worker n'existent plus")
    void routesDuWorkerSupprimees() throws Exception {
        String cle = serviceKeys.creer("worker", Set.of("read", "coach", "ingest"), null).cle();

        mvc.perform(get("/api/v1/ingest/targets").header("X-Service-Key", cle))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------

    private GarminSyncRun attendreUneTrace() throws Exception {
        for (int essai = 0; essai < 100; essai++) {
            List<GarminSyncRun> trouves = runs.findByAthleteIdOrderByDemarreADesc(
                    athleteId, org.springframework.data.domain.Limit.of(1));
            if (!trouves.isEmpty() && trouves.getFirst().getTermineA() != null) {
                return trouves.getFirst();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Aucune trace de passage n'est apparue en cinq secondes");
    }

    private String jetonAdmin() throws Exception {
        return jeton("admin@example.test");
    }

    private String jeton(String email) throws Exception {
        String corps = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corps).get("accessToken").asString();
    }
}
