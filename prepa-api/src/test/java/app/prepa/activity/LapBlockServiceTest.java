package app.prepa.activity;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.domain.IntensiteTour;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Regroupement des tours en blocs d'effort")
class LapBlockServiceTest {

    private final LapBlockService service = new LapBlockService();

    @Test
    @DisplayName("regroupe les tours consecutifs de meme intensite")
    void regroupement() {
        // Une seance de seuil : echauffement, trois fois deux kilometres, retour au calme.
        List<ActivityLap> tours = List.of(
                tour(1, 2000, 720, IntensiteTour.WARMUP, 128),
                tour(2, 1000, 315, IntensiteTour.INTERVAL, 152),
                tour(3, 1000, 318, IntensiteTour.INTERVAL, 156),
                tour(4, 500, 210, IntensiteTour.REST, 132),
                tour(5, 1000, 316, IntensiteTour.INTERVAL, 158),
                tour(6, 2000, 780, IntensiteTour.COOLDOWN, 125));

        List<LapBlockService.Bloc> blocs = service.grouper(tours);

        assertThat(blocs).hasSize(5);
        assertThat(blocs.get(1).intensite()).isEqualTo(IntensiteTour.INTERVAL);
        assertThat(blocs.get(1).nbTours()).isEqualTo(2);
        assertThat(blocs.get(1).distanceM()).isEqualTo(2000);
        assertThat(blocs.get(1).dureeSec()).isEqualTo(633);
    }

    @Test
    @DisplayName("recalcule l'allure du bloc sur ses totaux, pas sur la moyenne des tours")
    void allureDuBloc() {
        List<ActivityLap> tours = List.of(
                tour(1, 1000, 300, IntensiteTour.INTERVAL, 150),
                tour(2, 500, 180, IntensiteTour.INTERVAL, 155));

        LapBlockService.Bloc bloc = service.grouper(tours).getFirst();

        // 1500 m en 480 s, soit 320 s/km — et non la moyenne de 300 et 360.
        assertThat(bloc.allureSecKm()).isEqualTo(320);
    }

    @Test
    @DisplayName("pondere la frequence cardiaque par la duree de chaque tour")
    void fcPondereeParLaDuree() {
        // Un effort long a 160 et une breve reprise a 120 : la moyenne simple donnerait 140,
        // ce qui ne decrit pas le bloc.
        List<ActivityLap> tours = List.of(
                tour(1, 3000, 900, IntensiteTour.INTERVAL, 160),
                tour(2, 200, 60, IntensiteTour.INTERVAL, 120));

        LapBlockService.Bloc bloc = service.grouper(tours).getFirst();

        assertThat(bloc.fcMoy()).isEqualTo(157);
    }

    @Test
    @DisplayName("expose la derive cardiaque du premier au dernier tour")
    void deriveCardiaque() {
        List<ActivityLap> tours = List.of(
                tour(1, 1000, 300, IntensiteTour.ACTIVE, 140),
                tour(2, 1000, 302, IntensiteTour.ACTIVE, 148),
                tour(3, 1000, 305, IntensiteTour.ACTIVE, 155));

        LapBlockService.Bloc bloc = service.grouper(tours).getFirst();

        assertThat(bloc.fcDebut()).isEqualTo(140);
        assertThat(bloc.fcFin()).isEqualTo(155);
        assertThat(bloc.deriveFc()).isEqualTo(15);
    }

    @Test
    @DisplayName("ne reconnait comme structuree qu'une seance melangeant les intensites")
    void detectionDesSeancesStructurees() {
        List<ActivityLap> autoLap = List.of(
                tour(1, 1000, 360, IntensiteTour.ACTIVE, 135),
                tour(2, 1000, 358, IntensiteTour.ACTIVE, 138));
        List<ActivityLap> seuil = List.of(
                tour(1, 2000, 720, IntensiteTour.WARMUP, 128),
                tour(2, 1000, 315, IntensiteTour.INTERVAL, 152));

        assertThat(service.estStructuree(autoLap)).isFalse();
        assertThat(service.estStructuree(seuil)).isTrue();
    }

    private static ActivityLap tour(int index, int distanceM, int dureeSec, IntensiteTour intensite, int fc) {
        ActivityLap tour = new ActivityLap(UUID.randomUUID(), (short) index, dureeSec);
        tour.setDistanceM(distanceM);
        tour.setIntensite(intensite);
        tour.setFcMoy((short) fc);
        tour.setAllureSecKm(Math.round(dureeSec / (distanceM / 1000f)));
        return tour;
    }
}
