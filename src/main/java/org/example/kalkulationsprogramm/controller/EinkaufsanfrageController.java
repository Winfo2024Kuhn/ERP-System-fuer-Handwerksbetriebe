package org.example.kalkulationsprogramm.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufsanfrageService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf/anfragen")
@RequiredArgsConstructor
public class EinkaufsanfrageController {
    private final EinkaufsanfrageService anfragen;
    private final EinkaufBerechtigungService berechtigungen;
    @GetMapping public Page<Kopf> suchen(Pageable pageable, Authentication authentication) {
        berechtigungen.verlange(authentication, EinkaufBerechtigung.LESEN); return anfragen.suchen(pageable);
    }
    @GetMapping("/{id}") public Detail laden(@PathVariable Long id, Authentication authentication) {
        berechtigungen.verlange(authentication, EinkaufBerechtigung.LESEN); return anfragen.laden(id);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Detail anlegen(@RequestBody Create request, Authentication authentication) {
        return anfragen.anlegen(request, berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @PostMapping("/{id}/revisionen") public Detail revidieren(@PathVariable Long id, @RequestBody RevisionRequest request, Authentication authentication) {
        return anfragen.revidieren(id, request, berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Eingabefehler> handleEingabefehler(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new Eingabefehler(exception.getMessage(), List.of(new Feldfehler("request", exception.getMessage()))));
    }
    public record Eingabefehler(String message, List<Feldfehler> fieldErrors) {}
    public record Feldfehler(String field, String message) {}
}
