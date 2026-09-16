package app.prepa.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Entree de journal, datee au jour.
 *
 * <p>Le passage d'un journal hebdomadaire a des entrees datees colle au suivi continu :
 * une gene ressentie un mardi n'appartient pas a une semaine de plan, elle appartient a
 * un jour. {@code douleur} est un signal explicite : il declenche une question du coach.
 */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
public class JournalEntry {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String contenu;

    @Column
    private Short humeur;

    @Column
    private Short fatigue;

    @Column(name = "sommeil_h")
    private BigDecimal sommeilH;

    @Column(nullable = false)
    private boolean douleur;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected JournalEntry() {}

    public JournalEntry(UUID id, UUID athleteId, LocalDate date, String contenu) {
        this.id = id;
        this.athleteId = athleteId;
        this.date = date;
        this.contenu = contenu;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }
}
