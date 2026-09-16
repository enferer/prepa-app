package app.prepa.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.IntegrationTestBase;
import app.prepa.activity.Activity;
import app.prepa.activity.ActivityLap;
import app.prepa.activity.ActivityRepository;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteProfileService;
import app.prepa.athlete.AthleteRepository;
import app.prepa.athlete.ProfileDtos;
import app.prepa.domain.TypeActivite;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Synthese d'entrainement")
class AnalysisIntegrationTest extends IntegrationTestBase {

    @Autowired
    AthleteRepository athletes;

    @Autowired
    ActivityRepository activities;

    @Autowired
    AnalysisService analyse;

    @Autowired
    CoachContextService contexte;

    @Autowired
    AthleteProfileService profils;

    private UUID athleteId;

    @BeforeEach
    void preparer() {
        activities.deleteAll();
        athletes.deleteAll();
        Athlete athlete = new Athlete(UUID.randomUUID(), "analyse@example.com", "x", "Analyse");
        athletes.save(athlete);
        athleteId = athlete.getId();
    }

    @Test
    @DisplayName("compte le tapis et la piste dans le volume de course a pied")
    void tapisEtPisteComptent() {
        // Ces deux types etaient absents de la liste consultee par l'ancienne analyse :
        // les seances sur tapis et les fractionnes sur piste disparaissaient des syntheses.
        enregistrer(LocalDate.now().minusDays(3), TypeActivite.TREADMILL, 8000, 2880, 145);
        enregistrer(LocalDate.now().minusDays(2), TypeActivite.RUN, 10000, 3600, 150);
        enregistrer(LocalDate.now().minusDays(1), TypeActivite.TRAIL, 12000, 5400, 148);

        AnalysisDtos.Synthese synthese = analyse.analyser(athleteId, 90, null);

        assertThat(synthese.volume().nbCourses()).isEqualTo(3);
        assertThat(synthese.volume().kmTotal()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("ecarte la marche du volume de course")
    void marcheExclue() {
        enregistrer(LocalDate.now().minusDays(2), TypeActivite.RUN, 10000, 3600, 150);
        enregistrer(LocalDate.now().minusDays(1), TypeActivite.HIKE, 6000, 7200, 95);

        assertThat(analyse.analyser(athleteId, 90, null).volume().nbCourses()).isEqualTo(1);
    }

    @Test
    @DisplayName("mesure le seuil d'endurance relativement a la frequence cardiaque maximale")
    void seuilEnduranceRelatif() {
        // Un athlete dont la FC max est elevee : ses sorties faciles tournent a 150, au-dessus
        // du seuil fixe de 145 de l'ancienne analyse, qui les ecartait donc toutes.
        profils.mettreAJour(athleteId, new ProfileDtos.UpdateProfileRequest(
                (short) 200, null, null, null, null, null, null, null, null, null));
        enregistrer(LocalDate.now().minusDays(2), TypeActivite.RUN, 10000, 3600, 149);
        enregistrer(LocalDate.now().minusDays(1), TypeActivite.RUN, 12000, 4320, 148);

        AnalysisDtos.AllureEf ef = analyse.analyser(athleteId, 90, null).allureEf();

        assertThat(ef.seuilFcRetenu()).isEqualTo(150);
        assertThat(ef.seuilRelatif()).isTrue();
        assertThat(ef.nbSeances()).isEqualTo(2);
        assertThat(ef.allureMoySecKm()).isEqualTo(360);
    }

    @Test
    @DisplayName("retrouve le meilleur segment d'une sortie par fenetre glissante sur les tours")
    void recordSurFenetreGlissante() {
        // Quinze kilometres dont dix rapides au milieu : l'allure moyenne de la sortie ne peut
        // pas les reveler, seule une fenetre glissante sur les tours le peut.
        List<Integer> alluresParKm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            alluresParKm.add(400);
        }
        for (int i = 0; i < 10; i++) {
            alluresParKm.add(300);
        }
        for (int i = 0; i < 2; i++) {
            alluresParKm.add(400);
        }
        enregistrerAvecTours(LocalDate.now().minusDays(1), alluresParKm);

        AnalysisDtos.RecordEstime dixKm = analyse.analyser(athleteId, 90, null).records().stream()
                .filter(r -> r.distanceM() == 10000)
                .findFirst()
                .orElseThrow();

        assertThat(dixKm.surTours()).isTrue();
        assertThat(dixKm.tempsSec()).isEqualTo(3000);
        assertThat(dixKm.allureSecKm()).isEqualTo(300);
    }

    @Test
    @DisplayName("le contexte du coach reste borne")
    void contexteBorne() {
        for (int i = 1; i <= 60; i++) {
            enregistrer(LocalDate.now().minusDays(i), TypeActivite.RUN, 10000, 3600, 145);
        }

        CoachContextService.CoachContext ctx = contexte.construire(athleteId);

        // Le contexte est un resume, pas un export : il ne grandit pas avec l'historique.
        assertThat(ctx.journalRecent()).hasSizeLessThanOrEqualTo(5);
        assertThat(ctx.records()).hasSizeLessThanOrEqualTo(6);
        assertThat(ctx.athlete().nom()).isEqualTo("Analyse");
    }

    private void enregistrer(LocalDate date, TypeActivite type, int distanceM, int dureeSec, int fc) {
        Activity activite = new Activity(
                UUID.randomUUID(), athleteId, UUID.randomUUID().toString(),
                date.atTime(9, 0).toInstant(ZoneOffset.UTC), date, dureeSec);
        activite.setType(type);
        activite.setDistanceM(distanceM);
        activite.setFcMoy((short) fc);
        activite.setDenivelePosM(20);
        activite.setAllureMoySecKm(Math.round(dureeSec / (distanceM / 1000f)));
        activities.save(activite);
    }

    private void enregistrerAvecTours(LocalDate date, List<Integer> alluresParKm) {
        int duree = alluresParKm.stream().mapToInt(Integer::intValue).sum();
        Activity activite = new Activity(
                UUID.randomUUID(), athleteId, UUID.randomUUID().toString(),
                Instant.now(), date, duree);
        activite.setType(TypeActivite.RUN);
        activite.setDistanceM(alluresParKm.size() * 1000);
        activite.setAllureMoySecKm(duree / alluresParKm.size());

        List<ActivityLap> tours = new ArrayList<>();
        for (int i = 0; i < alluresParKm.size(); i++) {
            ActivityLap tour = new ActivityLap(UUID.randomUUID(), (short) (i + 1), alluresParKm.get(i));
            tour.setDistanceM(1000);
            tour.setAllureSecKm(alluresParKm.get(i));
            tours.add(tour);
        }
        activite.remplacerTours(tours);
        activities.save(activite);
    }
}
