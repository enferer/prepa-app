package app.prepa.garmin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Trace d'un passage de synchronisation, pour un athlete.
 *
 * <p>{@link GarminCredentials} ne garde que l'instant present — le dernier statut, le dernier
 * message. Tant que la synchronisation vivait dans un conteneur separe, savoir ce qui s'etait
 * passe la nuit precedente voulait dire lire ses journaux, s'ils n'avaient pas tourne. Une ligne
 * par athlete et par passage rend l'exploitation consultable : on voit la serie d'echecs qui
 * precede une panne, et on voit qui a relance a la main.
 *
 * <p>La ligne est ouverte en {@link StatutSync#EN_COURS} et fermee dans un {@code finally} : une
 * ligne restee en cours signale un processus tue, pas un passage qui dure.
 */
@Entity
@Table(name = "garmin_sync_runs")
@Getter
@Setter
public class GarminSyncRun {

    @Id private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "demarre_a", nullable = false)
    private Instant demarreA;

    @Column(name = "termine_a")
    private Instant termineA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeclencheurSync declencheur;

    /** Qui a demande, pour un passage manuel. Nul pour les passages automatiques. */
    @Column(name = "demande_par")
    private String demandePar;

    @Column(name = "fenetre_du")
    private LocalDate fenetreDu;

    @Column(name = "fenetre_au")
    private LocalDate fenetreAu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSync statut;

    private String message;

    private Integer recues;
    private Integer importees;

    @Column(name = "mises_a_jour")
    private Integer misesAJour;

    private Integer doublons;

    protected GarminSyncRun() {}

    public GarminSyncRun(UUID athleteId, DeclencheurSync declencheur, String demandePar) {
        this.id = UUID.randomUUID();
        this.athleteId = athleteId;
        this.declencheur = declencheur;
        this.demandePar = demandePar;
        this.demarreA = Instant.now();
        this.statut = StatutSync.EN_COURS;
    }
}
