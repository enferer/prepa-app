package app.prepa.athlete;

import app.prepa.domain.TypeSeance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Seance favorite de l'athlete, integree regulierement au plan en remplacement de la
 * seance de qualite du meme registre. Peut etre mise en pause quand une blessure la
 * contre-indique, sans etre supprimee.
 */
@Entity
@Table(name = "signature_sessions")
@Getter
@Setter
public class SignatureSession {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_seance", nullable = false)
    private TypeSeance typeSeance;

    @Column
    private String description;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "frequence_souhaitee")
    private String frequenceSouhaitee;

    @Column
    private String contexte;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SignatureSession() {}

    public SignatureSession(UUID id, UUID athleteId, String nom, TypeSeance typeSeance) {
        this.id = id;
        this.athleteId = athleteId;
        this.nom = nom;
        this.typeSeance = typeSeance;
    }
}
