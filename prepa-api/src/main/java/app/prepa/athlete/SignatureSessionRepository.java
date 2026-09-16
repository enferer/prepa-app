package app.prepa.athlete;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignatureSessionRepository extends JpaRepository<SignatureSession, UUID> {

    List<SignatureSession> findByAthleteId(UUID athleteId);

    List<SignatureSession> findByAthleteIdAndActifTrue(UUID athleteId);
}
