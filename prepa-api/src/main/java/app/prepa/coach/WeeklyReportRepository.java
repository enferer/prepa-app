package app.prepa.coach;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, UUID> {

    List<WeeklyReport> findByCycleIdOrderByDateDebutDesc(UUID cycleId);

    Optional<WeeklyReport> findByCycleIdAndDateDebut(UUID cycleId, LocalDate dateDebut);

    List<WeeklyReport> findByAthleteIdOrderByDateDebutDesc(UUID athleteId, Limit limit);
}
