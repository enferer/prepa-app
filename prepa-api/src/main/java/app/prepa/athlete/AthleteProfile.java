package app.prepa.athlete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Profil sportif de l'athlete, independant des cycles.
 *
 * <p>Ces donnees survivent d'un cycle a l'autre : les remettre dans le cycle, comme le
 * faisait {@code objectifs.json}, revenait a les perdre a chaque nouvelle preparation.
 */
@Entity
@Table(name = "athlete_profiles")
@Getter
@Setter
public class AthleteProfile {

    @Id
    @Column(name = "athlete_id")
    private UUID athleteId;

    @Column(name = "fc_max")
    private Short fcMax;

    @Column(name = "fc_repos")
    private Short fcRepos;

    @Column(name = "vma_kmh")
    private BigDecimal vmaKmh;

    @Column(name = "volume_habituel_km")
    private BigDecimal volumeHabituelKm;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "jours_disponibles", columnDefinition = "text[]")
    private String[] joursDisponibles;

    @Column(name = "renfo_actif", nullable = false)
    private boolean renfoActif;

    @Column(name = "renfo_frequence")
    private Short renfoFrequence;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "renfo_materiel", columnDefinition = "text[]")
    private String[] renfoMateriel;

    @Column(name = "renfo_focus")
    private String renfoFocus;

    @Column
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AthleteProfile() {}

    public AthleteProfile(UUID athleteId) {
        this.athleteId = athleteId;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    /**
     * Seuil de frequence cardiaque en dessous duquel une sortie est consideree en endurance
     * fondamentale. Relatif a la FC max quand elle est connue ; a defaut, le seuil fixe
     * historique de 145 bpm, qui n'est juste que pour un athlete de FC max moyenne.
     */
    public int seuilFcEnduranceFondamentale() {
        return fcMax != null ? Math.round(fcMax * 0.75f) : 145;
    }
}
