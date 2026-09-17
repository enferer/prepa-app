package app.prepa.garmin;

import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.infra.ApiException;
import app.prepa.infra.CryptoService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pilotage de la synchronisation Garmin : demandes, etat, identifiants servis au worker. */
@Service
public class GarminSyncService {

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
            String dernierStatut,
            String dernierMessage,
            boolean syncDemande) {}

    /** Ce dont le worker a besoin pour aller chercher les seances d'un athlete. */
    public record CibleSync(
            UUID athleteId,
            String athlete,
            String email,
            String motDePasse,
            String garminDisplayName,
            Instant derniereSync,
            boolean demandeExplicite) {}

    public record ResultatSync(
            UUID athleteId, String statut, String message, Integer activitesRecuperees) {}

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
        credentials.save(existant);
    }

    /** Leve le drapeau ; le worker le relevera a son passage suivant. */
    @Transactional
    public EtatSync demanderSync(UUID athleteId) {
        GarminCredentials cred = credentials.findById(athleteId)
                .orElseThrow(() -> ApiException.invalide(
                        "Aucun compte Garmin n'est relié à cet athlète : la synchronisation ne peut pas être lancée"));
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

    /**
     * Athletes a synchroniser, avec leurs identifiants dechiffres.
     *
     * <p>Reserve au worker : c'est le seul endroit ou ces secrets ressortent de la base.
     */
    @Transactional(readOnly = true)
    public List<CibleSync> ciblesASynchroniser(boolean seulementDemandes) {
        List<GarminCredentials> retenues =
                seulementDemandes ? credentials.findBySyncDemandeTrue() : credentials.findAll();
        return retenues.stream()
                .map(cred -> {
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
                            cred.isSyncDemande());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Enregistre le resultat d'un passage du worker et retombe le drapeau de demande.
     *
     * <p>Au premier sync reussi, le compte Garmin est memorise sur l'athlete. Aux suivants, un
     * compte different arrete le worker : c'est le garde-fou qui empeche d'ecrire les seances
     * d'un athlete dans l'historique d'un autre.
     */
    @Transactional
    public void enregistrerResultat(ResultatSync resultat, String garminDisplayName) {
        GarminCredentials cred = credentials.findById(resultat.athleteId())
                .orElseThrow(() -> ApiException.notFound("Identifiants Garmin"));
        cred.setDernierStatut(resultat.statut());
        cred.setDernierMessage(resultat.message());
        cred.setSyncDemande(false);
        if ("OK".equals(resultat.statut())) {
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
}
