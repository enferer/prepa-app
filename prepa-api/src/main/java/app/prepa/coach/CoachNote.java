package app.prepa.coach;

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
 * Une decision, observation ou consigne du coach, datee et bornee.
 *
 * <p>Remplace le champ de commentaires libres cumulatif qui grossissait sans fin et qu'il
 * fallait relire integralement a chaque point hebdomadaire. Trois garde-fous :
 * une portee qui determine la duree de vie, une longueur plafonnee, et un chainage explicite
 * quand une decision en annule une autre — plutot qu'une accumulation contradictoire.
 */
@Entity
@Table(name = "coach_notes")
@Getter
@Setter
public class CoachNote {

    /** Longueur maximale du contenu : une decision qui n'y tient pas est mal formulee. */
    public static final int CONTENU_MAX = 500;

    public static final int TITRE_MAX = 80;

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Portee portee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;

    @Column(nullable = false)
    private String titre;

    @Column(nullable = false)
    private String contenu;

    @Column(nullable = false)
    private boolean actif = true;

    /** Note que celle-ci remplace : la precedente est desactivee dans le meme geste. */
    @Column(name = "remplace_id")
    private UUID remplaceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CoachNote() {}

    public CoachNote(UUID id, UUID athleteId, LocalDate date, Portee portee, Categorie categorie,
            String titre, String contenu) {
        this.id = id;
        this.athleteId = athleteId;
        this.date = date;
        this.portee = portee;
        this.categorie = categorie;
        this.titre = titre;
        this.contenu = contenu;
    }

    public void desactiver() {
        this.actif = false;
    }

    /** Duree de vie d'une note. */
    public enum Portee {
        /** Regle permanente sur l'athlete. Toujours relue, doit rester rare. */
        DURABLE,
        /** Vraie pour le cycle courant seulement, archivee a sa cloture. */
        CYCLE,
        /** Contexte d'une semaine, purgee au-dela de quelques semaines. */
        PONCTUELLE
    }

    public enum Categorie {
        DECISION,
        OBSERVATION,
        CONSIGNE,
        ALERTE
    }
}
