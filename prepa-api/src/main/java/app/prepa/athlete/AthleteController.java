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
 * <p>Sert aux clients machine — les skills ont besoin de savoir sur qui ils travaillent — et
 * a l'application, ou elle alimente le choix du profil consulte : un athlete connecte voit
 * tous les autres, dont il pourra lire l'entrainement sans jamais le modifier.
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
                .filter(a -> principal.peutLire(a.getId()))
                .map(a -> principal.peutModifier(a.getId())
                        ? AthleteDtos.AthleteResponse.from(a)
                        : AthleteDtos.AthleteResponse.publique(a))
                .toList();
    }
}
