package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsPostfachDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizSpeichernRequest;
import org.example.kalkulationsprogramm.service.AbwesenheitsnotizService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * E-Mail-Einstellungen → Abwesenheitsnotizen. Für jeden angemeldeten Benutzer; welche
 * Postfächer und Notizen er sieht, regelt der {@link AbwesenheitsnotizService}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email/outofoffice")
public class OutOfOfficeController {

    private final AbwesenheitsnotizService service;

    @GetMapping
    public List<AbwesenheitsnotizDto> liste(Authentication authentication) {
        return service.liste(authentication);
    }

    /** Postfächer, die der Benutzer für eine Notiz auswählen darf. */
    @GetMapping("/postfaecher")
    public List<AbwesenheitsPostfachDto> postfaecher(Authentication authentication) {
        return service.waehlbarePostfaecher(authentication);
    }

    @PostMapping
    public AbwesenheitsnotizDto speichern(@RequestBody AbwesenheitsnotizSpeichernRequest request,
            Authentication authentication) {
        return service.speichern(request, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id, Authentication authentication) {
        service.loeschen(id, authentication);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(meldung(e, "Die Angaben sind unvollständig."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> nichtErlaubt(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(meldung(e, "Das dürfen Sie nicht."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> nichtGefunden(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(meldung(e, "Diese Abwesenheitsnotiz gibt es nicht mehr."));
    }

    private static Map<String, String> meldung(RuntimeException e, String ersatz) {
        return Map.of("message", Objects.requireNonNullElse(e.getMessage(), ersatz));
    }
}
