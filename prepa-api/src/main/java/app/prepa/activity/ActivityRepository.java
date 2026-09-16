package app.prepa.activity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByAthleteIdAndDedupKey(UUID athleteId, String dedupKey);

    Optional<Activity> findByAthleteIdAndGarminActivityId(UUID athleteId, Long garminActivityId);

    List<Activity> findByAthleteIdOrderByStartedAtDesc(UUID athleteId);

    @Query("select a.dedupKey from Activity a where a.athleteId = :athleteId")
    Set<String> dedupKeys(UUID athleteId);

    @Query("""
            select a from Activity a
            where a.athleteId = :athleteId and a.dateLocale between :debut and :fin
            order by a.startedAt asc
            """)
    List<Activity> entre(UUID athleteId, LocalDate debut, LocalDate fin);

    /** Activites jamais passees en revue par le coach. */
    @Query("""
            select a from Activity a
            where a.athleteId = :athleteId
              and not exists (select 1 from AnalysisState s where s.activityId = a.id)
            order by a.startedAt asc
            """)
    List<Activity> nonAnalysees(UUID athleteId);
}
