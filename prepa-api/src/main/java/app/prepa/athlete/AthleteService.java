package app.prepa.athlete;

import app.prepa.auth.Principal;
import app.prepa.infra.ApiException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AthleteService {

    private final AthleteRepository athletes;
    private final PasswordEncoder passwordEncoder;

    public AthleteService(AthleteRepository athletes, PasswordEncoder passwordEncoder) {
        this.athletes = athletes;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Athlete parId(UUID id) {
        return athletes.findById(id).orElseThrow(() -> ApiException.notFound("Athlète"));
    }

    /**
     * Charge un athlete apres verification que le principal a le droit d'ecrire chez lui.
     * C'est le point de passage unique du controle d'acces en ecriture.
     */
    @Transactional(readOnly = true)
    public Athlete modifiable(UUID id, Principal principal) {
        if (!principal.peutModifier(id)) {
            throw ApiException.forbidden("Cet athlète ne t'est accessible qu'en lecture");
        }
        return parId(id);
    }

    /**
     * Charge un athlete dont on veut seulement lire l'entrainement.
     *
     * <p>Le pendant en lecture de {@link #modifiable(UUID, Principal)}, et volontairement plus
     * permissif : voir ce que les autres courent est ouvert a tout athlete connecte. A reserver
     * aux routes d'entrainement — pour le journal, les blessures ou le profil, c'est
     * {@code modifiable} qui garde la porte, en lecture comme en ecriture.
     */
    @Transactional(readOnly = true)
    public Athlete lisible(UUID id, Principal principal) {
        if (!principal.peutLire(id)) {
            throw ApiException.forbidden("Cet athlète ne t'est pas accessible");
        }
        return parId(id);
    }

    @Transactional
    public Athlete creer(AthleteDtos.CreateAthleteRequest req) {
        if (athletes.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflit("Un athlète utilise déjà cet email");
        }
        Athlete athlete = new Athlete(
                UUID.randomUUID(), req.email(), passwordEncoder.encode(req.motDePasse()), req.displayName());
        if (req.role() != null) {
            athlete.setRole(req.role());
        }
        return athletes.save(athlete);
    }

    @Transactional
    public Athlete mettreAJour(UUID id, AthleteDtos.UpdateMeRequest req) {
        Athlete athlete = parId(id);
        if (req.displayName() != null) {
            athlete.setDisplayName(req.displayName());
        }
        if (req.timezone() != null) {
            athlete.setTimezone(req.timezone());
        }
        if (req.locale() != null) {
            athlete.setLocale(req.locale());
        }
        return athletes.save(athlete);
    }
}
