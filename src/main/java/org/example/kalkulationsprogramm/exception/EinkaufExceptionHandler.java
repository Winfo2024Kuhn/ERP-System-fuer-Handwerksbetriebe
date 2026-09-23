package org.example.kalkulationsprogramm.exception;

import java.util.Map;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.controller.EinkaufBerechtigungController;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = EinkaufBerechtigungController.class)
public class EinkaufExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Ungültiger Wert." : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(Map.of("message", "Bitte prüfe deine Eingaben.", "fieldErrors", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Die Anfrage enthält ungültige Daten.", "fieldErrors", Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", message(exception), "fieldErrors", Map.of()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> missing(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message(exception)));
    }

    @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message(exception)));
    }

    private String message(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Die Anfrage konnte nicht verarbeitet werden." : exception.getMessage();
    }
}
