package app.prepa.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** DTOs du module d'authentification. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String motDePasse) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSec) {}
}
