package app.prepa.cycle;

import app.prepa.domain.BlocEntrainement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Une semaine du plan, toujours calee sur un lundi.
 *
 * <p>{@code detaillee} distingue les deux regimes du plan glissant : une semaine detaillee
 * porte des seances datees, une semaine non detaillee ne porte que des cibles. En cycle libre,
 * seules les prochaines semaines sont detaillees — planifier une seance dans deux mois
 * n'aurait aucune valeur.
 */
@Entity
@Table(name = "training_weeks")
@Getter
@Setter
public class TrainingWeek {

    @Id
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(nullable = false)
    private short numero;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Enumerated(EnumType.STRING)
    @Column
    private BlocEntrainement bloc;

    @Column(name = "volume_cible_km", nullable = false)
    private BigDecimal volumeCibleKm;

    @Column(name = "nb_qualite_cible")
    private Short nbQualiteCible;

    @Column(name = "denivele_cible_m")
    private Integer deniveleCibleM;

    @Column(nullable = false)
    private boolean detaillee = true;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TrainingWeek() {}

    public TrainingWeek(UUID id, UUID cycleId, short numero, LocalDate dateDebut, BigDecimal volumeCibleKm) {
        this.id = id;
        this.cycleId = cycleId;
        this.numero = numero;
        this.dateDebut = dateDebut;
        this.volumeCibleKm = volumeCibleKm;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    /** Premier jour non couvert par la semaine — borne haute exclusive. */
    public LocalDate dateFinExclue() {
        return dateDebut.plusDays(7);
    }

    public boolean contient(LocalDate jour) {
        return !jour.isBefore(dateDebut) && jour.isBefore(dateFinExclue());
    }
}
