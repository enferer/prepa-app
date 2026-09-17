package app.prepa.infra;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Exception metier portant un code fonctionnel et un statut HTTP. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String quoi) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", quoi + " introuvable");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /** Champ reserve au coach, refuse a un principal athlete (cf. matrice de permissions). */
    public static ApiException forbiddenField(String champ) {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN_FIELD",
                "Ce champ ne peut être modifié que par ton coach",
                Map.of("champ", champ));
    }

    public static ApiException conflit(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException invalide(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
