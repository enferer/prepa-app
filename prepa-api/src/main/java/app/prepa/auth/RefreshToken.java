package app.prepa.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token, stocke sous forme d'empreinte : la valeur en clair ne vit que cote client. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(UUID id, UUID athleteId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.athleteId = athleteId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean estUtilisable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoquer() {
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
