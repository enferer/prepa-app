package app.prepa.auth;

import app.prepa.infra.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acces au principal de la requete courante. */
public final class CurrentPrincipal {

    private CurrentPrincipal() {}

    public static Principal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AuthenticatedPrincipal ap) {
            return ap.getPrincipal();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
    }
}
