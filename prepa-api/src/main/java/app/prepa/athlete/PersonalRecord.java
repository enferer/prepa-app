package app.prepa.athlete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Record personnel sur une distance, historise pour suivre la progression. */
@Entity
@Table(name = "personal_records")
@Getter
@Setter
public class PersonalRecord {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @Column(name = "temps_sec", nullable = false)
    private int tempsSec;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private String contexte;

    /** {@code DECLARE} si saisi par l'athlete, {@code ACTIVITE} si deduit d'une sortie. */
    @Column(nullable = false)
    private String source = "DECLARE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PersonalRecord() {}

    public PersonalRecord(UUID id, UUID athleteId, int distanceM, int tempsSec, LocalDate date) {
        this.id = id;
        this.athleteId = athleteId;
        this.distanceM = distanceM;
        this.tempsSec = tempsSec;
        this.date = date;
    }

    /** Allure du record, en secondes par kilometre. */
    public int allureSecKm() {
        return Math.round(tempsSec / (distanceM / 1000f));
    }
}
