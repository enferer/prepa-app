package app.prepa.athlete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTOs du module athlete. */
public final class AthleteDtos {

    private AthleteDtos() {}

    public record AthleteResponse(
            UUID id, String email, String displayName, String timezone, String locale, String role,
            String garminDisplayName) {

        public static AthleteResponse from(Athlete a) {
            return new AthleteResponse(
                    a.getId(), a.getEmail(), a.getDisplayName(), a.getTimezone(), a.getLocale(), a.getRole(),
                    a.getGarminDisplayName());
        }
    }

    public record UpdateMeRequest(String displayName, String timezone, String locale) {}

    public record CreateAthleteRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String motDePasse,
            @NotBlank String displayName,
            String role) {}
}
