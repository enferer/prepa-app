package app.prepa.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifie un client machine depuis l'en-tete {@code X-Service-Key}.
 *
 * <p>Ce filtre passe avant le filtre JWT : une requete portant une cle de service valide
 * est traitee comme venant du coach, quel que soit le reste.
 */
@Component
public class ServiceKeyAuthFilter extends OncePerRequestFilter {

    private static final String ENTETE = "X-Service-Key";

    private final ServiceKeyAuthenticator authenticator;

    public ServiceKeyAuthFilter(ServiceKeyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cle = request.getHeader(ENTETE);
        if (cle != null && !cle.isBlank()) {
            authenticator.authentifier(cle)
                    .map(AuthenticatedPrincipal::new)
                    .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        chain.doFilter(request, response);
    }
}
