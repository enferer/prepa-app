package app.prepa.athlete;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AthleteProfileRepository extends JpaRepository<AthleteProfile, UUID> {}
