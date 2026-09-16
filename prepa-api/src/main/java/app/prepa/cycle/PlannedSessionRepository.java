package app.prepa.cycle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlannedSessionRepository extends JpaRepository<PlannedSession, UUID> {

    List<PlannedSession> findByCycleIdOrderByDateAscOrdreAsc(UUID cycleId);

    List<PlannedSession> findByWeekIdOrderByDateAscOrdreAsc(UUID weekId);

    Optional<PlannedSession> findByActivityId(UUID activityId);

    @Query("""
            select s from PlannedSession s
            where s.cycleId = :cycleId and s.date between :debut and :fin
            order by s.date asc, s.ordre asc
            """)
    List<PlannedSession> entre(UUID cycleId, LocalDate debut, LocalDate fin);

    /** Seances d'un athlete sur une periode, tous cycles confondus. */
    @Query("""
            select s from PlannedSession s
            join Cycle c on c.id = s.cycleId
            where c.athleteId = :athleteId and s.date between :debut and :fin
            order by s.date asc, s.ordre asc
            """)
    List<PlannedSession> pourAthleteEntre(UUID athleteId, LocalDate debut, LocalDate fin);
}
