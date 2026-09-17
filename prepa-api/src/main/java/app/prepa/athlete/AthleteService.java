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
     * Charge un athlete apres verification que le principal a le droit d'y acceder.
     * C'est le point de passage unique du controle d'acces par athlete.
     */
    @Transactional(readOnly = true)
    public Athlete accessible(UUID id, Principal principal) {
        if (!principal.peutAcceder(id)) {
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
