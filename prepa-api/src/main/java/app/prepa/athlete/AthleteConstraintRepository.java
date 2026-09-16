package app.prepa.athlete;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AthleteConstraintRepository extends JpaRepository<AthleteConstraint, UUID> {

    List<AthleteConstraint> findByAthleteId(UUID athleteId);
}
