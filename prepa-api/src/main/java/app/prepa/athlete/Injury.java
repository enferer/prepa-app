package app.prepa.athlete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Blessure ou gene suivie.
 *
 * <p>Le {@code palier} reprend le protocole douleur en trois niveaux : 1 gene legere sans
 * alteration de la foulee, 2 douleur qui modifie la foulee ou persiste au repos,
 * 3 douleur aigue qui impose un avis professionnel.
 */
@Entity
@Table(name = "injuries")
@Getter
@Setter
public class Injury {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(nullable = false)
    private String zone;

    @Column(nullable = false)
    private String statut = "ACTIVE";

    @Column
    private Short palier;

    @Column
    private String consignes;

    @Column(nullable = false)
    private LocalDate debut;

    @Column
    private LocalDate fin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Injury() {}

    public Injury(UUID id, UUID athleteId, String zone, LocalDate debut) {
        this.id = id;
        this.athleteId = athleteId;
        this.zone = zone;
        this.debut = debut;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    public boolean estEnCours() {
        return !"RESOLUE".equals(statut);
    }
}
