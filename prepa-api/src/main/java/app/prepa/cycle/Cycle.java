package app.prepa.cycle;

import app.prepa.domain.LigneDirectrice;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.TypeCycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Une periode d'entrainement continue avec une intention.
 *
 * <p>C'est l'unite centrale du suivi. Un cycle {@link TypeCycle#PREPA} vise une course a une
 * date ; un cycle {@link TypeCycle#LIBRE} suit une ligne directrice sur un horizon choisi.
 * La structure de semaines et de seances est la meme dans les deux cas — seule la maniere
 * de construire le plan differe.
 */
@Entity
@Table(name = "cycles")
@Getter
@Setter
public class Cycle {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeCycle type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCycle statut = StatutCycle.PLANIFIE;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "course_nom")
    private String courseNom;

    @Column(name = "course_date")
    private LocalDate courseDate;

    @Column(name = "course_distance_m")
    private Integer courseDistanceM;

    @Column(name = "chrono_vise_sec")
    private Integer chronoViseSec;

    @Column(name = "ligne_directrice")
    private String ligneDirectrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "ligne_directrice_type")
    private LigneDirectrice ligneDirectriceType;

    @Column(name = "horizon_semaines")
    private Short horizonSemaines;

    /**
     * Allures cibles par zone : {@code {"EF": {"secKm": 400, "affichage": "6:40"}}}.
     * Reste un document : ses cles varient selon le cycle (un cycle trail parle de D+/h).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allures_cibles", nullable = false)
    private Map<String, Object> alluresCibles = new HashMap<>();

    @Column
    private String bilan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Cycle() {}

    public Cycle(UUID id, UUID athleteId, String slug, String nom, TypeCycle type,
            LocalDate dateDebut, LocalDate dateFin) {
        this.id = id;
        this.athleteId = athleteId;
        this.slug = slug;
        this.nom = nom;
        this.type = type;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    public boolean estPrepa() {
        return type == TypeCycle.PREPA;
    }

    public boolean estActif() {
        return statut == StatutCycle.ACTIF;
    }

    /** Nombre de jours avant la course, negatif une fois passee. Null hors preparation. */
    public Long joursAvantCourse() {
        return courseDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), courseDate);
    }

    /** Nombre de semaines que couvre le cycle, arrondi au superieur. */
    public int nbSemaines() {
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin);
        return (int) Math.max(1, Math.ceil(jours / 7.0));
    }
}
