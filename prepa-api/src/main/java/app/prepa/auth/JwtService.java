package app.prepa.auth;

import app.prepa.athlete.Athlete;
import app.prepa.infra.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Emission et verification des access tokens JWT (HS256). */
@Service
public class JwtService {

    private final SecretKey cle;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        byte[] secret = props.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("prepa.auth.jwt-secret doit faire au moins 32 octets");
        }
        this.cle = Keys.hmacShaKeyFor(secret);
        this.props = props;
    }

    public String emettreAccessToken(Athlete athlete) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(athlete.getId().toString())
                .claim("nom", athlete.getDisplayName())
                .claim("role", athlete.getRole())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(props.auth().accessTokenTtl())))
                .signWith(cle)
                .compact();
    }

    /** Renvoie le principal si le token est valide et non expire, vide sinon. */
    public Optional<Principal> verifier(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(cle).build().parseSignedClaims(token).getPayload();
            UUID athleteId = UUID.fromString(claims.getSubject());
            boolean admin = "ADMIN".equals(claims.get("role", String.class));
            return Optional.of(Principal.athlete(athleteId, admin, claims.get("nom", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
