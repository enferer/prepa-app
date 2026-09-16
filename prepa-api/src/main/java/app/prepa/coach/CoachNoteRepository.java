package app.prepa.coach;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachNoteRepository extends JpaRepository<CoachNote, UUID> {

    List<CoachNote> findByAthleteIdAndPorteeAndActifTrueOrderByDateDesc(
            UUID athleteId, CoachNote.Portee portee);

    List<CoachNote> findByAthleteIdAndPorteeAndActifTrueOrderByDateDesc(
            UUID athleteId, CoachNote.Portee portee, Limit limit);

    List<CoachNote> findByCycleIdAndPorteeAndActifTrueOrderByDateDesc(UUID cycleId, CoachNote.Portee portee);

    List<CoachNote> findByAthleteIdOrderByDateDesc(UUID athleteId);

    long countByAthleteIdAndPorteeAndActifTrue(UUID athleteId, CoachNote.Portee portee);

    /** Purge des notes ponctuelles trop anciennes. */
    void deleteByPorteeAndDateBefore(CoachNote.Portee portee, LocalDate avant);
}
