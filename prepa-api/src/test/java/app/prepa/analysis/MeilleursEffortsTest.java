package app.prepa.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.activity.Activity;
import app.prepa.activity.ActivityLap;
import app.prepa.analysis.AnalysisDtos.Provenance;
import app.prepa.analysis.AnalysisDtos.RecordEstime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D'ou viennent les meilleurs efforts, et lesquels ont le droit de gagner.
 *
 * <p>Ces tests fixent la lecon de l'ancien calcul : l'etiquette « mesure » y signalait
 * seulement que la montre avait transmis le detail des tours. Un dix kilometres couru pour
 * lui-meme passait pour une estimation faute de tours, tandis qu'un kilometre devale en
 * descente au milieu d'un trail s'affichait comme un record.
 */
class MeilleursEffortsTest {

    private static Activity sortie(String date, int distanceM, int allureSecKm, int denivelePos, int deniveleNeg) {
        Activity a = new Activity(
                UUID.randomUUID(), UUID.randomUUID(), date, Instant.parse(date + "T08:00:00Z"),
                LocalDate.parse(date), Math.round(allureSecKm * distanceM / 1000f));
        a.setDistanceM(distanceM);
        a.setAllureMoySecKm(allureSecKm);
        a.setDenivelePosM(denivelePos);
        a.setDeniveleNegM(deniveleNeg);
        return a;
    }

    private static void tour(Activity a, int index, int distanceM, int dureeSec, int denivelePos, int deniveleNeg) {
        ActivityLap tour = new ActivityLap(UUID.randomUUID(), (short) index, dureeSec);
        tour.setDistanceM(distanceM);
        tour.setDenivelePosM(denivelePos);
        tour.setDeniveleNegM(deniveleNeg);
        a.getTours().add(tour);
    }

    private static RecordEstime sur(List<RecordEstime> records, int distanceM) {
        return records.stream().filter(r -> r.distanceM() == distanceM).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Une sortie qui fait la distance donne un chrono, pas une estimation")
    void laSortieEstLeRecord() {
        // 10,13 km a 4:44 : c'est un dix kilometres couru pour lui-meme, meme sans detail de tours.
        RecordEstime dix = sur(AnalysisService.records(List.of(sortie("2026-04-26", 10130, 284, 30, 30))), 10000);

        assertThat(dix.provenance()).isEqualTo(Provenance.SORTIE);
        assertThat(dix.tempsSec()).isEqualTo(2840);
    }

    @Test
    @DisplayName("Au-delà de la marge, une sortie plus longue ne donne qu'une estimation")
    void auDelaDeLaMargeCEstUneEstimation() {
        // Un semi de 21,1 km ne temoigne pas d'un dix kilometres : son allure moyenne l'approche.
        RecordEstime dix = sur(AnalysisService.records(List.of(sortie("2026-02-04", 21130, 334, 40, 40))), 10000);

        assertThat(dix.provenance()).isEqualTo(Provenance.ESTIMATION);
    }

    @Test
    @DisplayName("Une estimation ne bat jamais une mesure, même plus rapide sur le papier")
    void lEstimationNeBatPasLaMesure() {
        Activity mesuree = sortie("2026-05-31", 5080, 266, 20, 20);
        Activity longue = sortie("2026-06-15", 30000, 260, 50, 50);

        RecordEstime cinq = sur(AnalysisService.records(List.of(longue, mesuree)), 5000);

        // L'extrapolation de la sortie longue donnerait 21:40, plus rapide que les 22:10 reellement
        // courus — et pourtant personne n'a jamais couru ce cinq kilometres-la.
        assertThat(cinq.provenance()).isEqualTo(Provenance.SORTIE);
        assertThat(cinq.tempsSec()).isEqualTo(1330);
    }

    @Test
    @DisplayName("Un kilomètre dévalé en descente n'est pas un record du kilomètre")
    void laDescenteNeFaitPasUnRecord() {
        Activity trail = sortie("2026-09-09", 5050, 420, 150, 150);
        tour(trail, 1, 1000, 480, 60, 0);
        // Un kilometre a 3:39 pour quatre-vingts metres de perdus : c'est la pente qui court.
        tour(trail, 2, 1000, 219, 0, 80);
        tour(trail, 3, 1000, 450, 40, 20);
        tour(trail, 4, 1000, 440, 30, 30);
        tour(trail, 5, 1050, 460, 20, 20);

        RecordEstime kilometre = sur(AnalysisService.records(List.of(trail)), 1000);

        assertThat(kilometre.tempsSec()).isGreaterThan(300);
    }

    @Test
    @DisplayName("Sur une sortie longue détaillée, la fenêtre glissante retrouve le meilleur segment")
    void laFenetreGlissanteTrouveLeSegment() {
        Activity sortieLongue = sortie("2026-08-22", 22000, 358, 60, 60);
        for (int i = 1; i <= 22; i++) {
            // Cinq kilometres nettement plus rapides au milieu d'une sortie longue tranquille.
            tour(sortieLongue, i, 1000, i >= 9 && i <= 13 ? 300 : 380, 3, 3);
        }

        RecordEstime cinq = sur(AnalysisService.records(List.of(sortieLongue)), 5000);

        assertThat(cinq.provenance()).isEqualTo(Provenance.TOURS);
        assertThat(cinq.tempsSec()).isEqualTo(1500);
    }
}
