package app.prepa.garmin;

import app.prepa.domain.IntensiteTour;
import app.prepa.domain.TypeActivite;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** DTOs d'ingestion : la forme sous laquelle une activite entre dans le systeme. */
public final class GarminDtos {

    private GarminDtos() {}

    /**
     * Une activite normalisee, prete a etre persistee.
     *
     * <p>C'est le contrat commun au parser CSV et a la synchronisation Garmin : les deux produisent
     * cette forme, le service d'ingestion ne connait qu'elle.
     */
    @Getter
    @Setter
    public static class ActiviteBrute {
        private Long garminActivityId;
        private String source = "GARMIN_API";
        private TypeActivite type = TypeActivite.OTHER;
        private String typeGarmin;
        private String titre;

        /** Heure locale de depart, telle que la montre l'a enregistree. */
        @NotNull private LocalDateTime startedAtLocal;

        @NotNull private Integer dureeSec;

        private Integer dureeMouvementSec;
        private Integer distanceM;
        private Integer allureMoySecKm;
        private Integer meilleureAllureSecKm;
        private Integer gapMoySecKm;
        private Short fcMoy;
        private Short fcMax;
        private Short fcMin;
        private Short cadenceMoy;
        private Short cadenceMax;
        private Integer denivelePosM;
        private Integer deniveleNegM;
        private Integer altitudeMinM;
        private Integer altitudeMaxM;
        private Integer calories;
        private Double teAerobie;
        private Double teAnaerobie;
        private String teLabel;
        private Double chargeEntrainement;
        private Double vo2max;
        private Double longueurFouleeM;
        private Double oscillationVerticale;
        private Short tempsContactSol;
        private Integer puissanceMoy;
        private Integer puissanceMax;
        private String lieu;
        private Map<String, Object> meteo;
        private List<Map<String, Object>> zonesFc;
        private List<TourBrut> tours;
    }

    /** Un tour tel que remonte par Garmin. */
    @Getter
    @Setter
    public static class TourBrut {
        private short index;
        private Integer distanceM;
        private int dureeSec;
        private Integer allureSecKm;
        private Integer gapSecKm;
        private Short fcMoy;
        private Short fcMax;
        private Short cadenceMoy;
        private Integer puissanceMoy;
        private Integer denivelePosM;
        private Integer deniveleNegM;
        private IntensiteTour intensite = IntensiteTour.UNKNOWN;
    }

    /** Compte rendu d'une ingestion. */
    public record ResultatIngestion(
            int recues, int importees, int doublons, int misesAJour, List<String> erreurs) {}
}
