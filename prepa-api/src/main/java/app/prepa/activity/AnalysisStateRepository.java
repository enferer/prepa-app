package app.prepa.activity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisStateRepository extends JpaRepository<AnalysisState, UUID> {

    long countByAthleteId(UUID athleteId);
}
