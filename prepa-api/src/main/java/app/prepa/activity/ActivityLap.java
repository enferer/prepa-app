package app.prepa.activity;

import app.prepa.domain.IntensiteTour;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un tour d'une seance.
 *
 * <p>C'est le niveau auquel on juge l'execution d'une seance a blocs : la moyenne d'une
 * seance d'intervalles ne dit rien d'utile.
 */
@Entity
@Table(name = "activity_laps")
@Getter
@Setter
public class ActivityLap {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "index_tour", nullable = false)
    private short indexTour;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "duree_sec", nullable = false)
    private int dureeSec;

    @Column(name = "allure_sec_km")
    private Integer allureSecKm;

    @Column(name = "gap_sec_km")
    private Integer gapSecKm;

    @Column(name = "fc_moy")
    private Short fcMoy;

    @Column(name = "fc_max")
    private Short fcMax;

    @Column(name = "cadence_moy")
    private Short cadenceMoy;

    @Column(name = "puissance_moy")
    private Integer puissanceMoy;

    @Column(name = "denivele_pos_m")
    private Integer denivelePosM;

    @Column(name = "denivele_neg_m")
    private Integer deniveleNegM;

    @Enumerated(EnumType.STRING)
    @Column
    private IntensiteTour intensite = IntensiteTour.UNKNOWN;

    protected ActivityLap() {}

    public ActivityLap(UUID id, short indexTour, int dureeSec) {
        this.id = id;
        this.indexTour = indexTour;
        this.dureeSec = dureeSec;
    }

    /** Denivele net signe du tour : positif en montee, negatif en descente. */
    public int deniveleNetM() {
        return (denivelePosM == null ? 0 : denivelePosM) - (deniveleNegM == null ? 0 : deniveleNegM);
    }
}
