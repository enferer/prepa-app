package app.prepa.athlete;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AthleteRepository extends JpaRepository<Athlete, UUID> {

    Optional<Athlete> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
