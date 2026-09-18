package app.prepa.athlete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTOs du module athlete. */
public final class AthleteDtos {

    private AthleteDtos() {}

    public record AthleteResponse(
            UUID id, String email, String username, String displayName, String timezone, String locale, String role,
            String garminDisplayName) {

        public static AthleteResponse from(Athlete a) {
            return new AthleteResponse(
                    a.getId(), a.getEmail(), a.getUsername(), a.getDisplayName(), a.getTimezone(), a.getLocale(),
                    a.getRole(), a.getGarminDisplayName());
        }

        /**
         * Ce qu'on montre d'un athlete qu'on ne fait que consulter : de quoi le nommer et le
         * choisir, rien de plus. L'email, le nom d'utilisateur et le compte Garmin identifient
         * la personne ailleurs qu'ici — les servir a tout le vestiaire pour alimenter un menu
         * serait gratuit, et les deux premiers ouvrent une session.
         */
        public static AthleteResponse publique(Athlete a) {
            return new AthleteResponse(
                    a.getId(), null, null, a.getDisplayName(), a.getTimezone(), a.getLocale(), a.getRole(), null);
        }
    }

    public record UpdateMeRequest(String displayName, String timezone, String locale) {}

    /** Le nom d'utilisateur est facultatif : laisse vide, il est derive du nom affiche. */
    public record CreateAthleteRequest(
            @Email @NotBlank String email,
            @Pattern(regexp = Username.FORMAT, message = "Nom d'utilisateur invalide") String username,
            @NotBlank @Size(min = 8) String motDePasse,
            @NotBlank String displayName,
            String role) {}

    /** Ce qu'un admin peut changer d'un compte qu'il n'a pas cree lui-meme. */
    public record UpdateAthleteRequest(
            @Pattern(regexp = Username.FORMAT, message = "Nom d'utilisateur invalide") String username) {}
}
