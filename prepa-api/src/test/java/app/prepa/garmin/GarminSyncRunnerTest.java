package app.prepa.garmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.prepa.IntegrationTestBase;
import app.prepa.activity.ActivityRepository;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.garmin.client.GarminAuth;
import app.prepa.garmin.client.GarminClient;
import app.prepa.garmin.client.GarminException;
import app.prepa.garmin.client.GarminJetons;
import app.prepa.garmin.client.GarminMfaEnAttente;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Un passage de synchronisation, Garmin bouchonne.
 *
 * <p>Le portail d'authentification ne peut pas etre simule : ce qui se verifie ici est tout le
 * reste, c'est-a-dire ce qui distingue un passage robuste d'un passage qui s'arrete au premier
 * incident — l'isolation des athletes, le garde-fou d'identite, l'idempotence, et le fait qu'un
 * passage laisse toujours une trace exploitable.
 */
@DisplayName("Passage de synchronisation Garmin")
class GarminSyncRunnerTest extends IntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GarminJetons JETONS =
            new GarminJetons("t", "s", "acces", Instant.now().plusSeconds(3600));

    @MockitoBean
    GarminAuth auth;

    @MockitoBean
    GarminClient garmin;

    @Autowired
    GarminSyncRunner runner;

    @Autowired
    GarminSyncService sync;

    @Autowired
    GarminSyncRunRepository runs;

    @Autowired
    AthleteRepository athletes;

    @Autowired
    ActivityRepository activities;

    @Autowired
    GarminCredentialsRepository credentials;

    private UUID anne;
    private UUID bruno;

    @BeforeEach
    void preparer() {
        runs.deleteAll();
        activities.deleteAll();
        credentials.deleteAll();
        athletes.deleteAll();

        anne = creerAthleteRelie("anne@example.test", "Anne");
        bruno = creerAthleteRelie("bruno@example.test", "Bruno");

        when(auth.ouvrir(any(), any(), any())).thenReturn(JETONS);
        when(garmin.displayName(any())).thenReturn("compte-garmin");
        when(garmin.activites(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("importe les séances rapportées et laisse une trace du passage")
    void importeEtTrace() {
        when(garmin.activites(any(), any(), any())).thenReturn(List.of(resume()));
        when(garmin.details(any(), anyLong())).thenReturn(details());

        int echecs = runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(echecs).isZero();
        assertThat(activities.count()).isEqualTo(1);

        GarminSyncRun trace = dernierePasse(anne);
        assertThat(trace.getStatut()).isEqualTo(StatutSync.OK);
        assertThat(trace.getDeclencheur()).isEqualTo(DeclencheurSync.MANUEL);
        assertThat(trace.getDemandePar()).isEqualTo("coach");
        assertThat(trace.getImportees()).isEqualTo(1);
        assertThat(trace.getTermineA()).isNotNull();
    }

    @Test
    @DisplayName("rejouer le même passage n'ajoute aucun doublon")
    void idempotence() {
        when(garmin.activites(any(), any(), any())).thenReturn(List.of(resume()));
        when(garmin.details(any(), anyLong())).thenReturn(details());

        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);
        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(activities.count()).isEqualTo(1);
        GarminSyncRun second = dernierePasse(anne);
        assertThat(second.getImportees()).isZero();
        assertThat(second.getDoublons() + second.getMisesAJour()).isEqualTo(1);
    }

    @Test
    @DisplayName("mémorise le compte Garmin au premier succès, et une seule fois")
    void gardeFouMemorise() {
        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(athletes.findById(anne).orElseThrow().getGarminDisplayName())
                .isEqualTo("compte-garmin");
    }

    @Test
    @DisplayName("refuse d'écrire les séances d'un autre compte, sans rien importer")
    void gardeFouRefuse() {
        Athlete athlete = athletes.findById(anne).orElseThrow();
        athlete.setGarminDisplayName("le-bon-compte");
        athletes.save(athlete);

        when(garmin.displayName(any())).thenReturn("un-autre-compte");

        int echecs = runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(echecs).isEqualTo(1);
        assertThat(activities.count()).isZero();
        assertThat(dernierePasse(anne).getStatut()).isEqualTo(StatutSync.IDENTITE_KO);
        // Rien n'est alle chercher de seances : le garde-fou coupe avant.
        verify(garmin, never()).activites(any(), any(), any());
        // Et l'identite memorisee n'a pas bouge.
        assertThat(athletes.findById(anne).orElseThrow().getGarminDisplayName())
                .isEqualTo("le-bon-compte");
    }

    @Test
    @DisplayName("un athlète en échec n'empêche pas les suivants")
    void isolationDesAthletes() {
        when(auth.ouvrir(any(), any(), any()))
                .thenThrow(GarminException.auth("Identifiants refusés"))
                .thenReturn(JETONS);

        int echecs = runner.passage(DeclencheurSync.PLANIFIE, false, null);

        assertThat(echecs).isEqualTo(1);
        // Les deux athletes ont ete traites, et les deux ont laisse une trace.
        assertThat(runs.count()).isEqualTo(2);
        assertThat(runs.findAll()).extracting(GarminSyncRun::getStatut)
                .containsExactlyInAnyOrder(StatutSync.AUTH_ERROR, StatutSync.OK);
    }

    @Test
    @DisplayName("met de côté la session quand Garmin réclame un code")
    void mfaEnAttente() {
        when(auth.ouvrir(any(), any(), any()))
                .thenThrow(new GarminMfaEnAttente("{\"cookies\":[]}"));

        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(dernierePasse(anne).getStatut()).isEqualTo(StatutSync.MFA_REQUISE);
        // Le contexte est conserve : sans lui, le code saisi plus tard ne se rattacherait a rien.
        assertThat(sync.contexteMfa(anne)).isEqualTo("{\"cookies\":[]}");
    }

    @Test
    @DisplayName("ne laisse jamais une trace en cours, même sur une erreur imprévue")
    void traceToujoursClose() {
        when(garmin.displayName(any())).thenThrow(new IllegalStateException("panne inattendue"));

        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        GarminSyncRun trace = dernierePasse(anne);
        assertThat(trace.getStatut()).isEqualTo(StatutSync.ERREUR);
        assertThat(trace.getTermineA()).isNotNull();
        assertThat(runner.enCours()).isFalse();
    }

    @Test
    @DisplayName("reprend la veille de la dernière synchronisation réussie")
    void fenetreAvecRecouvrement() {
        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);
        // Le premier passage vient de poser une date de synchronisation : le suivant doit repartir
        // de la veille, faute de quoi une montre synchronisée tardivement serait manquée.
        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", null);

        assertThat(dernierePasse(anne).getFenetreDu()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    @DisplayName("reprend quatre mois pour un athlète jamais synchronisé")
    void fenetrePremierPassage() {
        runner.passageCible(bruno, DeclencheurSync.MANUEL, "coach", null);

        assertThat(dernierePasse(bruno).getFenetreDu()).isEqualTo(LocalDate.now().minusDays(120));
    }

    @Test
    @DisplayName("honore une fenêtre imposée")
    void fenetreImposee() {
        LocalDate depuis = LocalDate.of(2026, 1, 15);

        runner.passageCible(anne, DeclencheurSync.MANUEL, "coach", depuis);

        assertThat(dernierePasse(anne).getFenetreDu()).isEqualTo(depuis);
    }

    // ------------------------------------------------------------------

    private UUID creerAthleteRelie(String email, String nom) {
        Athlete athlete = new Athlete(UUID.randomUUID(), email, "x", nom);
        athletes.save(athlete);
        sync.enregistrerIdentifiants(athlete.getId(), email, "motdepasse");
        return athlete.getId();
    }

    private GarminSyncRun dernierePasse(UUID athleteId) {
        List<GarminSyncRun> trouves = runs.findByAthleteIdOrderByDemarreADesc(
                athleteId, org.springframework.data.domain.Limit.of(1));
        assertThat(trouves).as("aucune trace de passage pour cet athlète").isNotEmpty();
        return trouves.getFirst();
    }

    private static JsonNode resume() {
        return lire("resume.json");
    }

    private static Map<String, JsonNode> details() {
        return Map.of(
                "splits", lire("splits.json"),
                "zonesFc", lire("zones.json"),
                "meteo", lire("meteo.json"),
                "detail", lire("detail.json"));
    }

    private static JsonNode lire(String fichier) {
        try (InputStream flux =
                GarminSyncRunnerTest.class.getResourceAsStream("/fixtures/garmin/" + fichier)) {
            return JSON.readTree(flux);
        } catch (Exception e) {
            throw new IllegalStateException("Fixture " + fichier + " illisible", e);
        }
    }
}
