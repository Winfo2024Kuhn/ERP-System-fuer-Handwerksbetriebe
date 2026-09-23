package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.BewertungRequest;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.EntnahmeDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.EntnahmeRequest;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufLagerentnahmeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/einkauf/lagerentnahmen")
@RequiredArgsConstructor
public class EinkaufLagerentnahmeController {
    private final EinkaufLagerentnahmeService entnahmeService;
    private final EinkaufBerechtigungService berechtigungService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntnahmeDto bestaetigen(@RequestBody EntnahmeRequest request, Authentication authentication) {
        Long akteurId = berechtigungService.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return entnahmeService.bestaetigen(request, akteurId);
    }

    @PutMapping("/{id}/bewertung")
    public EntnahmeDto bewertungErgaenzen(@PathVariable Long id, @RequestBody BewertungRequest request,
            Authentication authentication) {
        Long akteurId = berechtigungService.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return entnahmeService.bewertungErgaenzen(id, request, akteurId);
    }

    @GetMapping
    public Page<EntnahmeDto> suche(@RequestParam Long projektId, Pageable pageable, Authentication authentication) {
        berechtigungService.verlange(authentication, EinkaufBerechtigung.LESEN);
        return entnahmeService.suche(projektId, pageable);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Eingabefehler> handleEingabefehler(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new Eingabefehler(exception.getMessage(),
                List.of(new Feldfehler("request", exception.getMessage()))));
    }

    public record Eingabefehler(String message, List<Feldfehler> fieldErrors) {}
    public record Feldfehler(String field, String message) {}
}
