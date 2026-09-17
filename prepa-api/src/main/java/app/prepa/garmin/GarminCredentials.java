package app.prepa.garmin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Identifiants Garmin d'un athlete, chiffres au repos, et etat de sa synchronisation.
 *
 * <p>{@code syncDemande} porte les demandes a la volee : l'application ou un skill le leve, le
 * worker le releve a son passage suivant. Un drapeau plutot qu'un appel direct — le worker vit
 * ailleurs, et rien ne garantit qu'il soit joignable au moment ou la demande est faite.
 */
@Entity
@Table(name = "garmin_credentials")
@Getter
@Setter
public class GarminCredentials {

    @Id
    @Column(name = "athlete_id")
    private UUID athleteId;

    @Column(name = "email_enc", nullable = false)
    private byte[] emailEnc;

    @Column(name = "password_enc", nullable = false)
    private byte[] passwordEnc;

    @Column(name = "derniere_sync")
    private Instant derniereSync;

    @Column(name = "dernier_statut")
    private String dernierStatut;

    @Column(name = "dernier_message")
    private String dernierMessage;

    @Column(name = "sync_demande", nullable = false)
    private boolean syncDemande;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected GarminCredentials() {}

    public GarminCredentials(UUID athleteId, byte[] emailEnc, byte[] passwordEnc) {
        this.athleteId = athleteId;
        this.emailEnc = emailEnc;
        this.passwordEnc = passwordEnc;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }
}
