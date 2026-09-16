package app.prepa.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authentifie un athlete depuis l'en-tete {@code Authorization: Bearer <jwt>}. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String entete = request.getHeader("Authorization");
        if (entete != null && entete.startsWith(PREFIXE)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.verifier(entete.substring(PREFIXE.length()))
                    .map(AuthenticatedPrincipal::new)
                    .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        chain.doFilter(request, response);
    }
}
