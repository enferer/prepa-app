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
     * Un athlete n'accede qu'a lui-meme ; un admin et une cle de service globale accedent a tous ;
     * une cle de service nominative est limitee a son athlete.
     */
    public boolean peutAcceder(UUID cible) {
        return switch (kind) {
            case ADMIN -> true;
            case ATHLETE -> cible.equals(athleteId);
            case SERVICE -> athleteId == null || cible.equals(athleteId);
        };
    }
}
