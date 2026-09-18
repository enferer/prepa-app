package app.prepa.auth;

import jakarta.validation.constraints.NotBlank;

/** DTOs du module d'authentification. */
public final class AuthDtos {

    private AuthDtos() {}

    /**
     * Ce qu'on tape pour entrer : un email ou un nom d'utilisateur, au choix.
     *
     * <p>Le champ ne s'appelle donc plus {@code email} et n'est plus valide comme tel — c'est
     * la recherche en base qui tranche ce qu'on a saisi, pas le format.
     */
    public record LoginRequest(@NotBlank String identifiant, @NotBlank String motDePasse) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSec) {}
}
