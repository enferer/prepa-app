package app.prepa.garmin;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GarminCredentialsRepository extends JpaRepository<GarminCredentials, UUID> {

    List<GarminCredentials> findBySyncDemandeTrue();
}
