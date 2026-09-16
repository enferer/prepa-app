package app.prepa.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Jeton d'authentification Spring Security portant notre {@link Principal}. */
public class AuthenticatedPrincipal extends AbstractAuthenticationToken {

    private final transient Principal principal;

    public AuthenticatedPrincipal(Principal principal) {
        super(autorites(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> autorites(Principal principal) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.kind().name()));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Principal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.nom();
    }
}
