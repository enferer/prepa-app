package app.prepa.cycle;

import app.prepa.domain.StatutCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CycleRepository extends JpaRepository<Cycle, UUID> {

    List<Cycle> findByAthleteIdOrderByDateDebutDesc(UUID athleteId);

    Optional<Cycle> findByAthleteIdAndStatut(UUID athleteId, StatutCycle statut);

    Optional<Cycle> findByAthleteIdAndSlug(UUID athleteId, String slug);

    boolean existsByAthleteIdAndSlug(UUID athleteId, String slug);

    /** Le cycle couvrant une date donnee — sert a rattacher une activite a son contexte. */
    @Query("""
            select c from Cycle c
            where c.athleteId = :athleteId
              and c.dateDebut <= :jour and c.dateFin >= :jour
            order by c.dateDebut desc
            """)
    List<Cycle> couvrant(UUID athleteId, LocalDate jour);
}
