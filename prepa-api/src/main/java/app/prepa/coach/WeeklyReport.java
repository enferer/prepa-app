package app.prepa.coach;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bilan d'une semaine : ce que l'athlete lit, et la trace de ce que le coach a change. */
@Entity
@Table(name = "weekly_reports")
@Getter
@Setter
public class WeeklyReport {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "week_id")
    private UUID weekId;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private String bilan;

    @Column(name = "points_attention")
    private String pointsAttention;

    /** {@code [{seanceId, champ, avant, apres, raison}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<Map<String, Object>> changements;

    @Column
    private String consignes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WeeklyReport() {}

    public WeeklyReport(UUID id, UUID athleteId, UUID cycleId, LocalDate dateDebut, String bilan) {
        this.id = id;
        this.athleteId = athleteId;
        this.cycleId = cycleId;
        this.dateDebut = dateDebut;
        this.bilan = bilan;
    }
}
