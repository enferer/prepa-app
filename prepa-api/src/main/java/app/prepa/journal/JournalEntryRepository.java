package app.prepa.journal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByAthleteIdAndDate(UUID athleteId, LocalDate date);

    List<JournalEntry> findByAthleteIdOrderByDateDesc(UUID athleteId);

    List<JournalEntry> findByAthleteIdOrderByDateDesc(UUID athleteId, Limit limit);

    List<JournalEntry> findByAthleteIdAndDateBetweenOrderByDateAsc(UUID athleteId, LocalDate debut, LocalDate fin);
}
