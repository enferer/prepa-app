package app.prepa.garmin;

import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.garmin.client.GarminJetons;
import app.prepa.infra.ApiException;
import app.prepa.infra.CryptoService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Etat de la synchronisation Garmin : identifiants, jetons, demandes, resultats.
 *
 * <p>Le service garde la main sur les secrets — ils sont chiffres en base et ne sont dechiffres
 * qu'ici, dans le processus qui s'en sert. C'est un changement de fond par rapport au
 * synchroniseur precedent, qui vivait ailleurs et devait donc se les faire servir en clair par
 * une route HTTP dediee.
 */
@Service
public class GarminSyncService {

    /** Au-dela, une session laissee en attente d'un code n'a plus cours chez Garmin. */
    private static final int MFA_VALIDITE_MINUTES = 10;

    private final GarminCredentialsRepository credentials;
    private final AthleteRepository athletes;
    private final CryptoService crypto;

    public GarminSyncService(
            GarminCredentialsRepository credentials, AthleteRepository athletes, CryptoService crypto) {
        this.credentials = credentials;
        this.athletes = athletes;
        this.crypto = crypto;
    }

    /** Etat de synchronisation, tel que l'application et les skills le lisent. */
    public record EtatSync(
            UUID athleteId,
            String athlete,
            boolean configure,
            Instant derniereSync,
            StatutSync dernierStatut,
            String dernierMessage,
            boolean syncDemande) {}

    /** Ce dont un passage a besoin pour aller chercher les seances d'un athlete. */
    public record CibleSync(
            UUID athleteId,
            String athlete,
            String email,
            String motDePasse,
            String garminDisplayName,
            Instant derniereSync,
            GarminJetons jetons,
            boolean demandeExplicite) {}

    public record ResultatSync(
            UUID athleteId, StatutSync statut, String message, Integer activitesRecuperees) {}

    @Transactional
    public void enregistrerIdentifiants(UUID athleteId, String email, String motDePasse) {
        GarminCredentials existant = credentials.findById(athleteId).orElse(null);
        if (existant == null) {
            credentials.save(new GarminCredentials(
                    athleteId, crypto.chiffrer(email), crypto.chiffrer(motDePasse)));
            return;
        }
        existant.setEmailEnc(crypto.chiffrer(email));
        existant.setPasswordEnc(crypto.chiffrer(motDePasse));
        // Des identifiants qui changent invalident la session ouverte avec les anciens : la garder
        // ferait reussir la synchronisation sans jamais verifier la nouvelle saisie.
        existant.oublierLesJetons();
        credentials.save(existant);
    }

    /** Leve le drapeau, relu par le passage suivant si celui qu'on lance echoue a demarrer. */
    @Transactional
    public EtatSync demanderSync(UUID athleteId) {
        GarminCredentials cred = exigerCompte(athleteId);
        cred.setSyncDemande(true);
        credentials.save(cred);
        return etat(athleteId);
    }

    @Transactional(readOnly = true)
    public EtatSync etat(UUID athleteId) {
        Athlete athlete = athletes.findById(athleteId).orElseThrow(() -> ApiException.notFound("Athlète"));
        Optional<GarminCredentials> cred = credentials.findById(athleteId);
        return new EtatSync(
                athleteId,
                athlete.getDisplayName(),
                cred.isPresent(),
                cred.map(GarminCredentials::getDerniereSync).orElse(null),
                cred.map(GarminCredentials::getDernierStatut).orElse(null),
                cred.map(GarminCredentials::getDernierMessage).orElse(null),
                cred.map(GarminCredentials::isSyncDemande).orElse(false));
    }

    @Transactional(readOnly = true)
    public List<EtatSync> etatDeTous() {
        return credentials.findAll().stream()
                .map(cred -> athletes.findById(cred.getAthleteId()).map(athlete -> new EtatSync(
                                cred.getAthleteId(),
                                athlete.getDisplayName(),
                                true,
                                cred.getDerniereSync(),
                                cred.getDernierStatut(),
                                cred.getDernierMessage(),
                                cred.isSyncDemande()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Athletes a synchroniser, identifiants et jetons dechiffres. */
    @Transactional(readOnly = true)
    public List<CibleSync> ciblesASynchroniser(boolean seulementDemandes) {
        List<GarminCredentials> retenues =
                seulementDemandes ? credentials.findBySyncDemandeTrue() : credentials.findAll();
        return retenues.stream().map(this::versCible).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public CibleSync cible(UUID athleteId) {
        CibleSync cible = versCible(exigerCompte(athleteId));
        if (cible == null) {
            throw ApiException.notFound("Athlète");
        }
        return cible;
    }

    private CibleSync versCible(GarminCredentials cred) {
        Athlete athlete = athletes.findById(cred.getAthleteId()).orElse(null);
        if (athlete == null) {
            return null;
        }
        return new CibleSync(
                cred.getAthleteId(),
                athlete.getDisplayName(),
                crypto.dechiffrer(cred.getEmailEnc()),
                crypto.dechiffrer(cred.getPasswordEnc()),
                athlete.getGarminDisplayName(),
                cred.getDerniereSync(),
                jetons(cred),
                cred.isSyncDemande());
    }

    private GarminJetons jetons(GarminCredentials cred) {
        if (cred.getOauth1TokenEnc() == null) {
            return GarminJetons.vides();
        }
        return new GarminJetons(
                crypto.dechiffrer(cred.getOauth1TokenEnc()),
                cred.getOauth1SecretEnc() == null ? null : crypto.dechiffrer(cred.getOauth1SecretEnc()),
                cred.getOauth2TokenEnc() == null ? null : crypto.dechiffrer(cred.getOauth2TokenEnc()),
                cred.getOauth2ExpireAt());
    }

    /** Conserve la session ouverte, pour que le passage suivant se passe du mot de passe. */
    @Transactional
    public void enregistrerJetons(UUID athleteId, GarminJetons jetons) {
        credentials.findById(athleteId).ifPresent(cred -> {
            cred.setOauth1TokenEnc(jetons.oauth1Token() == null ? null : crypto.chiffrer(jetons.oauth1Token()));
            cred.setOauth1SecretEnc(jetons.oauth1Secret() == null ? null : crypto.chiffrer(jetons.oauth1Secret()));
            cred.setOauth2TokenEnc(jetons.oauth2Access() == null ? null : crypto.chiffrer(jetons.oauth2Access()));
            cred.setOauth2ExpireAt(jetons.expireA());
            cred.setMfaContexteEnc(null);
            cred.setMfaDemandeAt(null);
            credentials.save(cred);
        });
    }

    /** Met de cote une session SSO interrompue par une demande de code. */
    @Transactional
    public void memoriserContexteMfa(UUID athleteId, String contexte) {
        credentials.findById(athleteId).ifPresent(cred -> {
            cred.setMfaContexteEnc(crypto.chiffrer(contexte));
            cred.setMfaDemandeAt(Instant.now());
            credentials.save(cred);
        });
    }

    @Transactional(readOnly = true)
    public String contexteMfa(UUID athleteId) {
        GarminCredentials cred = exigerCompte(athleteId);
        if (cred.getMfaContexteEnc() == null || cred.getMfaDemandeAt() == null) {
            throw ApiException.invalide(
                    "Aucune vérification en deux étapes n'est en attente pour cet athlète");
        }
        if (cred.getMfaDemandeAt().isBefore(Instant.now().minus(MFA_VALIDITE_MINUTES, ChronoUnit.MINUTES))) {
            throw ApiException.invalide(
                    "La demande de vérification a expiré : relance une synchronisation pour en obtenir une nouvelle");
        }
        return crypto.dechiffrer(cred.getMfaContexteEnc());
    }

    /**
     * Enregistre l'issue d'un passage et retombe le drapeau de demande.
     *
     * <p>Au premier passage reussi, le compte Garmin est memorise sur l'athlete. Aux suivants, un
     * compte different arrete la synchronisation : c'est le garde-fou qui empeche d'ecrire les
     * seances d'un athlete dans l'historique d'un autre. Il n'est pose qu'une fois, et jamais a
     * partir d'un echec — sinon une erreur d'identifiants figerait la mauvaise identite.
     */
    @Transactional
    public void enregistrerResultat(ResultatSync resultat, String garminDisplayName) {
        GarminCredentials cred = credentials.findById(resultat.athleteId())
                .orElseThrow(() -> ApiException.notFound("Identifiants Garmin"));
        cred.setDernierStatut(resultat.statut());
        cred.setDernierMessage(resultat.message());
        cred.setSyncDemande(false);
        if (resultat.statut() == StatutSync.OK) {
            cred.setDerniereSync(Instant.now());
        }
        credentials.save(cred);

        if (garminDisplayName != null) {
            athletes.findById(resultat.athleteId()).ifPresent(athlete -> {
                if (athlete.getGarminDisplayName() == null) {
                    athlete.setGarminDisplayName(garminDisplayName);
                    athletes.save(athlete);
                }
            });
        }
    }

    /** Marque un passage comme demarre, pour que l'etat soit lisible pendant qu'il tourne. */
    @Transactional
    public void marquerEnCours(UUID athleteId) {
        credentials.findById(athleteId).ifPresent(cred -> {
            cred.setDernierStatut(StatutSync.EN_COURS);
            cred.setDernierMessage(null);
            credentials.save(cred);
        });
    }

    private GarminCredentials exigerCompte(UUID athleteId) {
        return credentials.findById(athleteId)
                .orElseThrow(() -> ApiException.invalide(
                        "Aucun compte Garmin n'est relié à cet athlète : la synchronisation ne peut pas être lancée"));
    }
}
