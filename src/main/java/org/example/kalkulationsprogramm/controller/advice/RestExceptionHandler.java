package org.example.kalkulationsprogramm.controller.advice;

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
