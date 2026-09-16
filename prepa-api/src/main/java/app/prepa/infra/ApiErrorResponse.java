package app.prepa.infra;

import java.util.Map;

/** Enveloppe d'erreur unique de l'API : {@code { "error": { code, message, details } }}. */
public record ApiErrorResponse(Body error) {

    public record Body(String code, String message, Map<String, Object> details) {}

    public static ApiErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ApiErrorResponse(new Body(code, message, details == null || details.isEmpty() ? null : details));
    }
}
