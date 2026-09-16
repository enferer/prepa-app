package app.prepa.athlete;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {

    List<PersonalRecord> findByAthleteIdOrderByDateDesc(UUID athleteId);

    /** Le meilleur temps connu sur une distance, tous millesimes confondus. */
    java.util.Optional<PersonalRecord> findFirstByAthleteIdAndDistanceMOrderByTempsSecAsc(
            UUID athleteId, int distanceM);
}
