package app.prepa.garmin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GarminSyncRunRepository extends JpaRepository<GarminSyncRun, UUID> {

    List<GarminSyncRun> findByOrderByDemarreADesc(Limit limite);

    List<GarminSyncRun> findByAthleteIdOrderByDemarreADesc(UUID athleteId, Limit limite);

    @Modifying
    @Query("delete from GarminSyncRun r where r.demarreA < :avant")
    int purger(Instant avant);
}
