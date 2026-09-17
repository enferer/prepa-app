package app.prepa.athlete;

import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AthleteService athleteService;

    public MeController(AthleteService athleteService) {
        this.athleteService = athleteService;
    }

    @GetMapping
    public AthleteDtos.AthleteResponse moi() {
        return AthleteDtos.AthleteResponse.from(athleteService.parId(athleteCourant()));
    }

    @PatchMapping
    public AthleteDtos.AthleteResponse majMoi(@Valid @RequestBody AthleteDtos.UpdateMeRequest req) {
        return AthleteDtos.AthleteResponse.from(athleteService.mettreAJour(athleteCourant(), req));
    }

    /** {@code /me} n'a de sens que pour un humain connecte : une cle globale n'a pas d'identite. */
    private java.util.UUID athleteCourant() {
        Principal principal = CurrentPrincipal.get();
        if (principal.athleteId() == null) {
            throw ApiException.invalide("Cette clé de service n'est rattachée à aucun athlète");
        }
        return principal.athleteId();
    }
}
