package app.prepa.activity;

import app.prepa.domain.IntensiteTour;
import app.prepa.domain.TypeActivite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs de consultation des activites. */
public final class ActivityDtos {

    private ActivityDtos() {}

    /** Vue de liste : ce qu'il faut pour reconnaitre une seance, pas davantage. */
    public record ActivityResume(
            UUID id,
            Long garminActivityId,
            TypeActivite type,
            String titre,
            Instant startedAt,
            LocalDate date,
            int dureeSec,
            Integer distanceM,
            Integer allureMoySecKm,
            Short fcMoy,
            Integer denivelePosM,
            boolean aDetail,
            Short rpe) {

        public static ActivityResume from(Activity a) {
            return new ActivityResume(
                    a.getId(), a.getGarminActivityId(), a.getType(), a.getTitre(), a.getStartedAt(),
                    a.getDateLocale(), a.getDureeSec(), a.getDistanceM(), a.getAllureMoySecKm(), a.getFcMoy(),
                    a.getDenivelePosM(), a.isADetail(), a.getRpe());
        }
    }

    /** Fiche complete : tours bruts, blocs d'effort, zones de frequence cardiaque, meteo. */
    public record ActivityDetail(
            ActivityResume resume,
            Integer dureeMouvementSec,
            Integer meilleureAllureSecKm,
            Integer gapMoySecKm,
            Short fcMax,
            Short fcMin,
            Short cadenceMoy,
            Integer deniveleNegM,
            Integer altitudeMinM,
            Integer altitudeMaxM,
            Integer calories,
            BigDecimal teAerobie,
            BigDecimal teAnaerobie,
            String teLabel,
            BigDecimal chargeEntrainement,
            BigDecimal vo2max,
            String lieu,
            String ressenti,
            Map<String, Object> meteo,
            List<Map<String, Object>> zonesFc,
            List<TourResponse> tours,
            boolean structuree,
            List<LapBlockService.Bloc> blocs) {}

    public record TourResponse(
            short index,
            Integer distanceM,
            int dureeSec,
            Integer allureSecKm,
            Integer gapSecKm,
            Short fcMoy,
            Short fcMax,
            Short cadenceMoy,
            Integer denivelePosM,
            Integer deniveleNegM,
            int deniveleNetM,
            IntensiteTour intensite) {

        public static TourResponse from(ActivityLap t) {
            return new TourResponse(
                    t.getIndexTour(), t.getDistanceM(), t.getDureeSec(), t.getAllureSecKm(), t.getGapSecKm(),
                    t.getFcMoy(), t.getFcMax(), t.getCadenceMoy(), t.getDenivelePosM(), t.getDeniveleNegM(),
                    t.deniveleNetM(), t.getIntensite());
        }
    }

    /** Ressenti saisi apres coup par l'athlete. */
    public record FeedbackRequest(@Min(1) @Max(10) Short rpe, String ressenti) {}
}
