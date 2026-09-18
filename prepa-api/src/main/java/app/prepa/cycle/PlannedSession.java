package app.prepa.cycle;

import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeSeance;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Une seance prevue au plan.
 *
 * <p>{@code alluresTexte} est volontairement une chaine d'affichage
 * ({@code "Seuil 5:20 · EF 6:40"}), a ne pas confondre avec {@code Cycle.alluresCibles},
 * qui est un dictionnaire de zones. Confondre les deux etait le piege numero un du
 * contrat de donnees precedent, ou les deux portaient le meme nom.
 *
 * <p>{@code activityId} porte le rapprochement avec l'activite reellement realisee.
 */
@Entity
@Table(name = "planned_sessions")
@Getter
@Setter
public class PlannedSession {

    /**
     * Longueur maximale du commentaire de coach : une ou deux phrases. Un commentaire lu
     * sous une seance est un verdict, pas une analyse — ce qui demande plus de place est
     * une decision, et une decision vit dans une {@code CoachNote}.
     */
    public static final int COMMENTAIRE_COACH_MAX = 280;

    @Id
    private UUID id;

    @Column(name = "week_id", nullable = false)
    private UUID weekId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(nullable = false)
    private LocalDate date;

    /** Depart lorsqu'il y a plusieurs seances le meme jour. */
    @Column(nullable = false)
    private short ordre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeSeance type;

    @Column(nullable = false)
    private String titre;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSeance statut = StatutSeance.A_VENIR;

    @Column(name = "allures_texte")
    private String alluresTexte;

    /**
     * Le deroule de la seance, pose par le coach.
     *
     * <p>Null tant qu'il ne l'a pas ecrit : le deroule est alors relu dans la description, ce
     * qui reste le cas de toutes les seances d'avant cette colonne. Voir {@link StructureSeance}.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column
    private List<BlocSeance> structure;

    @Column(name = "distance_cible_km")
    private BigDecimal distanceCibleKm;

    @Column(name = "duree_cible_min")
    private Short dureeCibleMin;

    /** Obligatoire pour un renforcement : une seance de renfo sans focus n'est pas executable. */
    @Column
    private String focus;

    @Column(name = "commentaire_coach")
    private String commentaireCoach;

    @Column(name = "commentaire_athlete")
    private String commentaireAthlete;

    @Column(name = "signature_session_id")
    private UUID signatureSessionId;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(nullable = false)
    private String rapprochement = "AUCUN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlannedSession() {}

    public PlannedSession(UUID id, UUID weekId, UUID cycleId, LocalDate date, TypeSeance type, String titre) {
        this.id = id;
        this.weekId = weekId;
        this.cycleId = cycleId;
        this.date = date;
        this.type = type;
        this.titre = titre;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    /** Le renfo ne compte pas dans le volume de course ni dans l'assiduite. */
    public boolean estCourseAPied() {
        return type != TypeSeance.RENFO && type != TypeSeance.REPOS && type != TypeSeance.CROSS;
    }

    public void rapprocherDe(UUID activityId, boolean manuel) {
        this.activityId = activityId;
        this.rapprochement = manuel ? "MANUEL" : "AUTO";
    }

    public void detacher() {
        this.activityId = null;
        this.rapprochement = "AUCUN";
    }

    /**
     * Ce qu'un ensemble de seances totalise en kilometres — le volume que le plan
     * <em>detaille</em>, a ne pas confondre avec celui qu'il <em>vise</em>.
     *
     * <p>Une seance annulee est sortie du plan : elle ne compte plus. Une seance deplacee, si.
     * Le renfo n'a pas de distance et s'ecarte de lui-meme.
     */
    public static BigDecimal volumePlanifie(List<PlannedSession> seances) {
        return seances.stream()
                .filter(s -> s.getStatut() != StatutSeance.ANNULEE)
                .map(PlannedSession::getDistanceCibleKm)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
