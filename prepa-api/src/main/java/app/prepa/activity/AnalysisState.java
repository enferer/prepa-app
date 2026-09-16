package app.prepa.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Marque une activite comme deja passee en revue, pour ne pas la reanalyser. */
@Entity
@Table(name = "analysis_state")
@Getter
public class AnalysisState {

    @Id
    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "analysee_le", nullable = false)
    private Instant analyseeLe = Instant.now();

    protected AnalysisState() {}

    public AnalysisState(UUID activityId, UUID athleteId) {
        this.activityId = activityId;
        this.athleteId = athleteId;
    }
}
