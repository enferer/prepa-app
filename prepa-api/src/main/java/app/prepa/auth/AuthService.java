package app.prepa.auth;

import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.infra.ApiException;
import app.prepa.infra.AppProperties;
import app.prepa.infra.Hashing;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Connexion, renouvellement et deconnexion. */
@Service
public class AuthService {

    private final AthleteRepository athletes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            AthleteRepository athletes,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties props) {
        this.athletes = athletes;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
    }

    @Transactional
    public AuthDtos.TokenResponse login(String email, String motDePasse) {
        Athlete athlete = athletes.findByEmailIgnoreCase(email)
                .filter(a -> passwordEncoder.matches(motDePasse, a.getPasswordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email ou mot de passe incorrect"));
        return emettre(athlete);
    }

    /**
     * Echange un refresh token contre un nouveau couple de jetons.
     * L'ancien refresh est revoque dans la foulee : rotation stricte, un refresh ne sert qu'une fois.
     */
    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stocke = refreshTokens.findByTokenHash(Hashing.sha256(refreshToken))
                .filter(RefreshToken::estUtilisable)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token invalide ou expire"));
        stocke.revoquer();
        Athlete athlete = athletes.findById(stocke.getAthleteId())
                .orElseThrow(() -> ApiException.notFound("Athlete"));
        return emettre(athlete);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(RefreshToken::revoquer);
    }

    private AuthDtos.TokenResponse emettre(Athlete athlete) {
        String accessToken = jwtService.emettreAccessToken(athlete);
        String refresh = genererRefresh();
        Instant expiration = Instant.now().plus(props.auth().refreshTokenTtl());
        refreshTokens.save(new RefreshToken(UUID.randomUUID(), athlete.getId(), Hashing.sha256(refresh), expiration));
        return new AuthDtos.TokenResponse(
                accessToken, refresh, props.auth().accessTokenTtl().toSeconds());
    }

    private String genererRefresh() {
        byte[] octets = new byte[32];
        random.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
