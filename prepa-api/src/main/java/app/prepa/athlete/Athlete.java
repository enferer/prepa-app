package app.prepa.athlete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un athlete : identite de connexion et rattachement au compte Garmin. */
@Entity
@Table(name = "athletes")
public class Athlete {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String timezone = "Europe/Paris";

    @Column(nullable = false)
    private String locale = "fr-FR";

    @Column(nullable = false)
    private String role = "ATHLETE";

    /** Compte Garmin memorise au premier sync : garde-fou contre le melange d'athletes. */
    @Column(name = "garmin_display_name")
    private String garminDisplayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Athlete() {}

    public Athlete(UUID id, String email, String passwordHash, String displayName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    @PrePersist
    void aLaCreation() {
        Instant maintenant = Instant.now();
        this.createdAt = maintenant;
        this.updatedAt = maintenant;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }

    public boolean estAdmin() {
        return "ADMIN".equals(role);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getGarminDisplayName() {
        return garminDisplayName;
    }

    public void setGarminDisplayName(String garminDisplayName) {
        this.garminDisplayName = garminDisplayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
