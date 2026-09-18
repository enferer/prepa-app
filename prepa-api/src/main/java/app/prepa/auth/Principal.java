package app.prepa.auth;

import java.util.Set;
import java.util.UUID;

/**
 * Le demandeur d'une requete authentifiee.
 *
 * <p>Deux formes coexistent : un athlete connecte via JWT ({@link Kind#ATHLETE}, ou
 * {@link Kind#ADMIN}) et un client machine porteur d'une cle de service
 * ({@link Kind#SERVICE} — skills Claude Code).
 *
 * <p>La distinction porte la matrice de permissions : seul un principal SERVICE peut
 * toucher a ce qui releve d'une decision de coach (structure du plan, allures, cycles).
 */
public record Principal(Kind kind, UUID athleteId, Set<String> scopes, String nom) {

    public enum Kind {
        ATHLETE,
        ADMIN,
        SERVICE
    }

    public static Principal athlete(UUID athleteId, boolean admin, String nom) {
        return new Principal(admin ? Kind.ADMIN : Kind.ATHLETE, athleteId, Set.of(), nom);
    }

    public static Principal service(String nom, Set<String> scopes, UUID athleteId) {
        return new Principal(Kind.SERVICE, athleteId, scopes, nom);
    }

    /** Vrai pour les clients machine : ils portent la voix du coach. */
    public boolean estCoach() {
        return kind == Kind.SERVICE;
    }

    public boolean aLeScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Peut-on agir sur les donnees de cet athlete ?
     * Un athlete n'ecrit que chez lui ; un admin et une cle de service globale ecrivent partout ;
     * une cle de service nominative est limitee a son athlete.
     */
    public boolean peutModifier(UUID cible) {
        return switch (kind) {
            case ADMIN -> true;
            case ATHLETE -> cible.equals(athleteId);
            case SERVICE -> athleteId == null || cible.equals(athleteId);
        };
    }

    /**
     * Peut-on lire l'entrainement de cet athlete ?
     *
     * <p>Plus large que {@link #peutModifier(UUID)} : les athletes se voient entre eux, parce
     * que se comparer fait partie de l'entrainement. La lecture ouverte ne porte que sur ce
     * qui releve de l'entrainement — cycles, seances, activites, analyses. Ce qui appartient
     * a la personne plutot qu'a sa course — journal, blessures, profil, comptes Garmin —
     * reste garde par {@code peutModifier}.
     *
     * <p>Une cle de service, elle, ne gagne rien ici : nominative, elle reste bornee a son
     * athlete, faute de quoi un skill ouvert pour l'un lirait l'historique de tous.
     */
    public boolean peutLire(UUID cible) {
        return switch (kind) {
            case ADMIN, ATHLETE -> true;
            case SERVICE -> athleteId == null || cible.equals(athleteId);
        };
    }
}
