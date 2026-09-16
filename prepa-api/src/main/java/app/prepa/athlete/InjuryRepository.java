package app.prepa.athlete;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InjuryRepository extends JpaRepository<Injury, UUID> {

    List<Injury> findByAthleteIdOrderByDebutDesc(UUID athleteId);

    List<Injury> findByAthleteIdAndStatutNot(UUID athleteId, String statut);
}
