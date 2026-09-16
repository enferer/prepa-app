package app.prepa.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cle d'acces machine : skills Claude Code, worker Garmin.
 *
 * <p>{@code athleteId} nul signifie une cle globale (le worker synchronise tous les athletes) ;
 * renseigne, la cle est limitee a cet athlete.
 */
@Entity
@Table(name = "service_keys")
public class ServiceKey {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] scopes;

    @Column(name = "athlete_id")
    private UUID athleteId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ServiceKey() {}

    public ServiceKey(UUID id, String nom, String keyHash, Set<String> scopes, UUID athleteId) {
        this.id = id;
        this.nom = nom;
        this.keyHash = keyHash;
        this.scopes = scopes.toArray(String[]::new);
        this.athleteId = athleteId;
    }

    public boolean estActive() {
        return revokedAt == null;
    }

    public void marquerUtilisee() {
        this.lastUsedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public Set<String> getScopes() {
        return scopes == null ? Set.of() : Set.of(scopes);
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
