package app.prepa.infra;

import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

/** Mappe toutes les exceptions vers l'enveloppe d'erreur unique. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> champs = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> champs.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_FAILED", "Requête invalide", champs));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_FAILED", "Requête invalide", Map.of()));
    }

    /**
     * Corps de requete illisible : JSON malforme, ou valeur hors des valeurs admises pour un
     * champ enumere. C'est une faute de l'appelant, pas une panne du serveur.
     *
     * <p>Le cas enum est le plus frequent en pratique (un skill qui devine une categorie) et le
     * plus facile a rendre actionnable : Jackson sait deja quelles valeurs etaient attendues,
     *{@code MALFORMED_REQUEST} seul obligeait a aller lire le code source pour les trouver.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleCorpsIllisible(HttpMessageNotReadableException ex) {
        log.debug("Corps de requête illisible", ex);
        if (ex.getCause() instanceof InvalidFormatException enumEx && enumEx.getTargetType() != null
                && enumEx.getTargetType().isEnum()) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (!enumEx.getPath().isEmpty()) {
                details.put("champ", enumEx.getPath().get(enumEx.getPath().size() - 1).getPropertyName());
            }
            details.put("valeursAcceptees",
                    Arrays.stream(enumEx.getTargetType().getEnumConstants()).map(Object::toString).toList());
            return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                    "MALFORMED_REQUEST", "Valeur non reconnue pour un champ énuméré", details));
        }
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("MALFORMED_REQUEST", "Corps de requête illisible", Map.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("UNAUTHENTICATED", "Authentification requise", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("FORBIDDEN", "Accès refusé", Map.of()));
    }

    /**
     * URL inconnue.
     *
     * <p>Sans ce cas, une route supprimee tombait dans le fourre-tout ci-dessous : l'appelant
     * recevait un 500 — qui signifie « le serveur est en panne », donc « reessaie » — la ou il
     * fallait lui dire que la route n'existe plus. Et chaque appel d'un client reste sur une
     * ancienne version noircissait les journaux d'« Erreur non geree ».
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRouteInconnue(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "Cette route n'existe pas", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAutre(Exception ex) {
        log.error("Erreur non geree", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Erreur interne", Map.of()));
    }
}
