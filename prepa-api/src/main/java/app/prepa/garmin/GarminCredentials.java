package app.prepa.garmin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p>{@code syncDemande} porte les demandes a la volee : un skill le leve, le passage suivant le
 * releve. Le drapeau a survecu a la disparition du worker parce qu'il sert maintenant de filet :
 * la synchronisation tourne en tache de fond, et un redemarrage du serveur en plein passage
 * laisserait sinon la demande sans suite.
 *
 * <p>Les jetons vivaient dans un volume Docker cote worker. Les ramener ici n'est pas qu'un
 * deplacement : l'OAuth1 vaut environ un an, et c'est lui qui evite de rejouer le mot de passe a
 * chaque passage — ce que Garmin finit par sanctionner en exigeant une verification en deux
 * etapes. Le perdre, c'est se condamner a la 2FA.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "dernier_statut")
    private StatutSync dernierStatut;

    @Column(name = "dernier_message")
    private String dernierMessage;

    @Column(name = "sync_demande", nullable = false)
    private boolean syncDemande;

    @Column(name = "oauth1_token_enc")
    private byte[] oauth1TokenEnc;

    @Column(name = "oauth1_secret_enc")
    private byte[] oauth1SecretEnc;

    @Column(name = "oauth2_token_enc")
    private byte[] oauth2TokenEnc;

    @Column(name = "oauth2_expire_at")
    private Instant oauth2ExpireAt;

    /** Session SSO laissee en suspens par une demande de code : cookies et jeton anti-rejeu. */
    @Column(name = "mfa_contexte_enc")
    private byte[] mfaContexteEnc;

    @Column(name = "mfa_demande_at")
    private Instant mfaDemandeAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected GarminCredentials() {}

    public GarminCredentials(UUID athleteId, byte[] emailEnc, byte[] passwordEnc) {
        this.athleteId = athleteId;
        this.emailEnc = emailEnc;
        this.passwordEnc = passwordEnc;
    }

    /**
     * Oublie toute session ouverte avec Garmin.
     *
     * <p>Appele quand le mot de passe change : garder les jetons laisserait la synchronisation
     * reussir avec d'anciens identifiants, et masquerait une erreur de saisie jusqu'au jour ou
     * le jeton expire.
     */
    public void oublierLesJetons() {
        this.oauth1TokenEnc = null;
        this.oauth1SecretEnc = null;
        this.oauth2TokenEnc = null;
        this.oauth2ExpireAt = null;
        this.mfaContexteEnc = null;
        this.mfaDemandeAt = null;
    }

    @PreUpdate
    void aLaMaj() {
        this.updatedAt = Instant.now();
    }
}
