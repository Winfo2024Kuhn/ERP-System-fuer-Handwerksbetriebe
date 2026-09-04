package org.example.kalkulationsprogramm.controller.advice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.example.kalkulationsprogramm.dto.ApiError;
import org.example.kalkulationsprogramm.exception.MietabrechnungValidationException;
import org.example.kalkulationsprogramm.util.ConstraintErrorDetail;
import org.example.kalkulationsprogramm.util.ConstraintMessageResolver;
import org.example.kalkulationsprogramm.util.FieldErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class RestExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    @Nullable
    private final ConstraintMessageResolver constraintMessageResolver;

    public RestExceptionHandler(@Nullable ConstraintMessageResolver constraintMessageResolver) {
        this.constraintMessageResolver = constraintMessageResolver;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (constraintMessageResolver == null) {
            ApiError fallback = new ApiError(HttpStatus.CONFLICT.value(), "Constraint violation", null, List.of(), ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(fallback);
        }
        ConstraintErrorDetail detail = constraintMessageResolver.resolve(ex);
        LOG.debug("Resolved data integrity violation: {}", detail);
        return ResponseEntity.status(detail.status()).body(toApiError(detail));
    }

    /**
     * Optimistisches Sperren (siehe @Version auf den Aggregate-Roots): eine
     * zweite, parallele Aenderung an derselben Zeile wurde bereits
     * gespeichert, bevor dieser Request sein eigenes Speichern versucht hat.
     * Faengt damit auch JpaOptimisticLockingFailureException ab -- die
     * Unterklasse, die Spring Data JPA beim echten Versionskonflikt ueber
     * EntityManagerFactoryUtils tatsaechlich wirft.
     *
     * <p>Sibling von DataIntegrityViolationException unter DataAccessException
     * (der eine Zweig laeuft ueber NonTransientDataAccessException, der
     * andere ueber TransientDataAccessException/ConcurrencyFailureException)
     * -- keiner ist Ober- oder Unterklasse des anderen, es gibt also keine
     * Ueberdeckung im ExceptionHandler-Resolver.</p>
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        LOG.debug("Versionskonflikt beim Speichern (optimistisches Sperren), Entitaetstyp: {}, Id: {}",
                ex.getPersistentClassName(), ex.getIdentifier());
        ApiError body = new ApiError(
                HttpStatus.CONFLICT.value(),
                "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.",
                null,
                List.of(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ApiError.Field> fields = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String field = error.getField();
            String label = humanize(field);
            String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Ungueltiger Wert.";
            fields.add(new ApiError.Field(field, label, message));
        }
        ApiError body = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Die Eingaben sind unvollstaendig oder ungueltig.",
                null,
                fields,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Verletzte Bean-Validation an @RequestParam/@PathVariable (z.B. @Min/@Max
     * an einem mit @Validated annotierten Controller). Ohne diesen Handler
     * laeuft das als HTTP 500 durch, obwohl es eine reine Eingabepruefung ist.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.Field> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = letztesPfadsegment(violation.getPropertyPath().toString());
            fields.add(new ApiError.Field(field, humanize(field), violation.getMessage()));
        }
        ApiError body = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Die Eingaben sind unvollstaendig oder ungueltig.",
                null,
                fields,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** "berechne.jahr" -> "jahr" */
    private static String letztesPfadsegment(String propertyPath) {
        int punkt = propertyPath.lastIndexOf('.');
        return punkt >= 0 ? propertyPath.substring(punkt + 1) : propertyPath;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        ApiError body = new ApiError(
                ex.getStatusCode().value(),
                ex.getReason(),
                null,
                List.of(),
                ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(MietabrechnungValidationException.class)
    public ResponseEntity<ApiError> handleMietabrechnungValidation(MietabrechnungValidationException ex) {
        ApiError body = new ApiError(
                ex.getStatus().value(),
                ex.getUserMessage(),
                null,
                List.of(),
                ex.getDetail()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    private static ApiError toApiError(ConstraintErrorDetail detail) {
        List<ApiError.Field> fields = detail.fieldErrors().stream()
                .map(RestExceptionHandler::toApiField)
                .toList();
        return new ApiError(
                detail.status().value(),
                detail.userMessage(),
                detail.constraintName(),
                fields,
                detail.technicalMessage()
        );
    }

    private static ApiError.Field toApiField(FieldErrorDetail detail) {
        return new ApiError.Field(detail.field(), detail.label(), detail.message());
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return value;
        }
        StringBuilder builder = new StringBuilder(cleaned.length());
        boolean capitalizeNext = true;
        for (char c : cleaned.toCharArray()) {
            if (Character.isWhitespace(c)) {
                builder.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toTitleCase(c));
                capitalizeNext = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}
