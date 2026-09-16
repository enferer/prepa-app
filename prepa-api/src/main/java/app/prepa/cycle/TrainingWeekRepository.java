package app.prepa.cycle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrainingWeekRepository extends JpaRepository<TrainingWeek, UUID> {

    List<TrainingWeek> findByCycleIdOrderByNumeroAsc(UUID cycleId);

    Optional<TrainingWeek> findByCycleIdAndNumero(UUID cycleId, short numero);

    Optional<TrainingWeek> findByCycleIdAndDateDebut(UUID cycleId, LocalDate dateDebut);

    void deleteByCycleId(UUID cycleId);

    /** Derniere semaine detaillee : point de reprise du plan glissant. */
    @Query("""
            select w from TrainingWeek w
            where w.cycleId = :cycleId and w.detaillee = true
            order by w.numero desc
            limit 1
            """)
    Optional<TrainingWeek> derniereDetaillee(UUID cycleId);
}
