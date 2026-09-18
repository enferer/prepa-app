package app.prepa.activity;

import app.prepa.domain.TypeActivite;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Une seance reellement effectuee.
 *
 * <p>Rattachee a l'athlete et non a un cycle : l'historique est continu et traverse les
 * cycles, ce qui permet de comparer une periode a une autre.
 *
 * <p>La deduplication passe par {@code dedupKey}. Quand Garmin fournit un identifiant
 * d'activite, c'est lui ; sinon (import CSV historique, saisie manuelle) c'est une empreinte
 * de date, duree et distance. L'ancienne jointure par date-heure a la seconde pres etait
 * fragile.
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
public class Activity {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "garmin_activity_id")
    private Long garminActivityId;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(nullable = false)
    private String source = "GARMIN_API";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeActivite type = TypeActivite.OTHER;

    @Column(name = "type_garmin")
    private String typeGarmin;

    @Column
    private String titre;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Date locale de la seance, materialisee pour les agregats et le rapprochement au plan. */
    @Column(name = "date_locale", nullable = false)
    private LocalDate dateLocale;

    @Column(name = "duree_sec", nullable = false)
    private int dureeSec;

    @Column(name = "duree_mouvement_sec")
    private Integer dureeMouvementSec;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "allure_moy_sec_km")
    private Integer allureMoySecKm;

    @Column(name = "meilleure_allure_sec_km")
    private Integer meilleureAllureSecKm;

    @Column(name = "gap_moy_sec_km")
    private Integer gapMoySecKm;

    @Column(name = "fc_moy")
    private Short fcMoy;

    @Column(name = "fc_max")
    private Short fcMax;

    @Column(name = "fc_min")
    private Short fcMin;

    @Column(name = "cadence_moy")
    private Short cadenceMoy;

    @Column(name = "cadence_max")
    private Short cadenceMax;

    @Column(name = "denivele_pos_m")
    private Integer denivelePosM;

    @Column(name = "denivele_neg_m")
    private Integer deniveleNegM;

    @Column(name = "altitude_min_m")
    private Integer altitudeMinM;

    @Column(name = "altitude_max_m")
    private Integer altitudeMaxM;

    @Column
    private Integer calories;

    @Column(name = "te_aerobie")
    private BigDecimal teAerobie;

    @Column(name = "te_anaerobie")
    private BigDecimal teAnaerobie;

    @Column(name = "te_label")
    private String teLabel;

    @Column(name = "charge_entrainement")
    private BigDecimal chargeEntrainement;

    @Column
    private BigDecimal vo2max;

    @Column(name = "longueur_foulee_m")
    private BigDecimal longueurFouleeM;

    @Column(name = "oscillation_verticale")
    private BigDecimal oscillationVerticale;

    @Column(name = "temps_contact_sol")
    private Short tempsContactSol;

    @Column(name = "puissance_moy")
    private Integer puissanceMoy;

    @Column(name = "puissance_max")
    private Integer puissanceMax;

    @Column
    private String lieu;

    /** Ressenti d'effort, saisi par l'athlete (1 a 10). */
    @Column
    private Short rpe;

    @Column
    private String ressenti;

    @Column(name = "a_detail", nullable = false)
    private boolean aDetail;

    /** {@code {temperatureC, ressentiC, humidite, ventKmh, description}} — converti en metrique. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> meteo;

    /** {@code [{zone, secondes, borneBasse}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "zones_fc")
    private List<Map<String, Object>> zonesFc;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("indexTour asc")
    private List<ActivityLap> tours = new ArrayList<>();

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Activity() {}

    public Activity(UUID id, UUID athleteId, String dedupKey, Instant startedAt, LocalDate dateLocale, int dureeSec) {
        this.id = id;
        this.athleteId = athleteId;
        this.dedupKey = dedupKey;
        this.startedAt = startedAt;
        this.dateLocale = dateLocale;
        this.dureeSec = dureeSec;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    public double distanceKm() {
        return distanceM == null ? 0 : distanceM / 1000.0;
    }

    /** Denivele net signe de la sortie : positif si elle monte plus qu'elle ne descend. */
    public int deniveleNetM() {
        return (denivelePosM == null ? 0 : denivelePosM) - (deniveleNegM == null ? 0 : deniveleNegM);
    }

    public void remplacerTours(List<ActivityLap> nouveaux) {
        tours.clear();
        nouveaux.forEach(t -> {
            t.setActivity(this);
            tours.add(t);
        });
        this.aDetail = !nouveaux.isEmpty();
    }
}
