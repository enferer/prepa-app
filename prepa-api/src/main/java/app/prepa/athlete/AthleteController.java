package app.prepa.athlete;

import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liste des athletes accessibles au demandeur.
 *
 * <p>Sert surtout aux clients machine : les skills ont besoin de savoir sur qui ils
 * travaillent. Un athlete connecte ne se voit que lui-meme.
 */
@RestController
@RequestMapping("/api/v1/athletes")
public class AthleteController {

    private final AthleteRepository athletes;

    public AthleteController(AthleteRepository athletes) {
        this.athletes = athletes;
    }

    @GetMapping
    public List<AthleteDtos.AthleteResponse> lister() {
        Principal principal = CurrentPrincipal.get();
        return athletes.findAll().stream()
                .filter(a -> principal.peutAcceder(a.getId()))
                .map(AthleteDtos.AthleteResponse::from)
                .toList();
    }
}
