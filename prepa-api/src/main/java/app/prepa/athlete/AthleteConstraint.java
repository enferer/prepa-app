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

/** Contrainte pesant sur l'entrainement : vacances, chaleur, materiel, contraintes pro. */
@Entity
@Table(name = "athlete_constraints")
@Getter
@Setter
public class AthleteConstraint {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(nullable = false)
    private String type = "AUTRE";

    @Column
    private LocalDate debut;

    @Column
    private LocalDate fin;

    @Column(nullable = false)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AthleteConstraint() {}

    public AthleteConstraint(UUID id, UUID athleteId, String type, String detail) {
        this.id = id;
        this.athleteId = athleteId;
        this.type = type;
        this.detail = detail;
    }

    /** Une contrainte sans dates est permanente. */
    public boolean concerne(LocalDate jour) {
        return (debut == null || !jour.isBefore(debut)) && (fin == null || !jour.isAfter(fin));
    }
}
