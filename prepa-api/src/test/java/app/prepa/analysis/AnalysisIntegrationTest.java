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
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleRepository;
import app.prepa.cycle.PlannedSession;
import app.prepa.cycle.PlannedSessionRepository;
import app.prepa.cycle.TrainingWeek;
import app.prepa.cycle.TrainingWeekRepository;
import app.prepa.domain.IntensiteTour;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.TypeActivite;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    CycleRepository cycles;

    @Autowired
    TrainingWeekRepository semaines;

    @Autowired
    PlannedSessionRepository seances;

    private UUID athleteId;

    @BeforeEach
    void preparer() {
        seances.deleteAll();
        semaines.deleteAll();
        cycles.deleteAll();
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

        assertThat(dixKm.provenance()).isEqualTo(AnalysisDtos.Provenance.TOURS);
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

    @Test
    @DisplayName("mesure l'allure marathon sur ses blocs, pas sur la sortie entiere")
    void allureMarathonSurLesBlocs() {
        // Une seance d'allure marathon telle qu'elle se court : echauffement, les blocs, retour
        // au calme. Moyenner la sortie entiere donnait 5:00 la ou l'athlete a tenu 4:30 sur ses
        // blocs — et l'affichait comme un retard sur une cible qu'il depassait.
        Activity activite = enregistrerSeanceStructuree(
                LocalDate.now().minusDays(1),
                List.of(360, 360),
                List.of(270, 270, 270, 270, 270, 270),
                List.of(360, 360));
        planifier(TypeSeance.AM, activite, 280);

        AnalysisDtos.AllureParType am = allureDe(TypeSeance.AM);

        assertThat(am.surLesBlocsDEffort()).isTrue();
        assertThat(am.allureReelleSecKm()).isEqualTo(270);
        assertThat(am.ecartSecKm()).isEqualTo(-10);
        assertThat(am.nbEcartees()).isZero();
    }

    @Test
    @DisplayName("ecarte une seance a allure marathon courue en tours automatiques")
    void allureMarathonSansStructure() {
        // Tous les tours de meme intensite : rien ne distingue les blocs du reste, et reprendre
        // la moyenne de la sortie reviendrait a la presenter comme une allure marathon.
        Activity activite = enregistrerSeanceStructuree(
                LocalDate.now().minusDays(1), List.of(), List.of(), List.of());
        planifier(TypeSeance.AM, activite, 280);

        AnalysisDtos.AllureParType am = allureDe(TypeSeance.AM);

        assertThat(am.allureReelleSecKm()).isNull();
        assertThat(am.nbEcartees()).isEqualTo(1);
    }

    private AnalysisDtos.AllureParType allureDe(TypeSeance type) {
        return analyse.analyser(athleteId, 90, null).alluresParType().stream()
                .filter(l -> l.type().equals(type.name()))
                .findFirst()
                .orElse(new AnalysisDtos.AllureParType(
                        type.name(), type.name(), 0, null, null, null, true, 0, false));
    }

    /** Un cycle actif d'une semaine, et la seance qui accomplit l'activite donnee. */
    private void planifier(TypeSeance type, Activity activite, int allureCibleSecKm) {
        LocalDate lundi = LocalDate.now().minusDays(7);
        Cycle cycle = new Cycle(
                UUID.randomUUID(), athleteId, "test-" + type.name().toLowerCase(java.util.Locale.ROOT),
                "Cycle de test", TypeCycle.LIBRE, lundi, lundi.plusWeeks(1));
        cycle.setStatut(StatutCycle.ACTIF);
        cycle.setLigneDirectrice("Maintenir la charge");
        cycle.setAlluresCibles(Map.of(type.name(), Map.of("secKm", allureCibleSecKm)));
        cycles.save(cycle);

        TrainingWeek semaine = new TrainingWeek(
                UUID.randomUUID(), cycle.getId(), (short) 1, lundi, BigDecimal.valueOf(40));
        semaines.save(semaine);

        PlannedSession seance = new PlannedSession(
                UUID.randomUUID(), semaine.getId(), cycle.getId(), activite.getDateLocale(), type, "Seance");
        seance.rapprocherDe(activite.getId(), false);
        seances.save(seance);
    }

    /**
     * Une sortie decoupee en trois temps, chaque liste donnant les allures de ses kilometres.
     * Des listes d'echauffement et de retour au calme vides donnent une sortie d'une seule
     * intensite — le cas des tours automatiques.
     */
    private Activity enregistrerSeanceStructuree(
            LocalDate date, List<Integer> echauffement, List<Integer> effort, List<Integer> retour) {
        List<ActivityLap> tours = new ArrayList<>();
        int duree = 0;
        int index = 1;
        for (var temps : List.of(
                Map.entry(IntensiteTour.WARMUP, echauffement.isEmpty() ? List.of(300, 300, 300) : echauffement),
                Map.entry(IntensiteTour.INTERVAL, effort),
                Map.entry(IntensiteTour.COOLDOWN, retour))) {
            for (int allureKm : temps.getValue()) {
                ActivityLap tour = new ActivityLap(UUID.randomUUID(), (short) index++, allureKm);
                tour.setDistanceM(1000);
                tour.setAllureSecKm(allureKm);
                tour.setIntensite(echauffement.isEmpty() ? IntensiteTour.ACTIVE : temps.getKey());
                tours.add(tour);
                duree += allureKm;
            }
        }

        Activity activite = new Activity(
                UUID.randomUUID(), athleteId, UUID.randomUUID().toString(),
                Instant.now(), date, duree);
        activite.setType(TypeActivite.RUN);
        activite.setDistanceM(tours.size() * 1000);
        activite.setDenivelePosM(20);
        activite.setAllureMoySecKm(duree / tours.size());
        activite.remplacerTours(tours);
        return activities.save(activite);
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
