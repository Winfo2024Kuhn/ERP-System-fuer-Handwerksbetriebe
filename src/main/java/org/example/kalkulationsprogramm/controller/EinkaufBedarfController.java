package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeichnungsbedarfService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/einkauf/bedarf")
@RequiredArgsConstructor
public class EinkaufBedarfController {
    private final EinkaufBedarfService bedarfService;
    private final EinkaufBerechtigungService berechtigungService;
    private final EinkaufZeichnungsbedarfService zeichnungsbedarfe;

    @GetMapping
    public Page<EinkaufBedarfDto.Response> suche(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long projektId, Pageable pageable, Authentication authentication) {
        berechtigungService.verlange(authentication, EinkaufBerechtigung.LESEN);
        return bedarfService.suche(q, projektId, pageable);
    }

    @GetMapping("/{id}")
    public EinkaufBedarfDto.Response laden(@PathVariable Long id, Authentication authentication) {
        berechtigungService.verlange(authentication, EinkaufBerechtigung.LESEN);
        return bedarfService.laden(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EinkaufBedarfDto.Response anlegen(@RequestBody EinkaufBedarfDto.Create request,
            Authentication authentication) {
        Long akteurId = berechtigungService.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return bedarfService.anlegen(request, akteurId);
    }

    @PostMapping(value = "/zeichnungsteil", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EinkaufBedarfDto.Response zeichnungsteilAnlegen(@RequestPart("bedarf") EinkaufBedarfDto.Create request,
            @RequestPart("datei") MultipartFile datei, @RequestParam String revision,
            Authentication authentication) {
        Long akteurId = berechtigungService.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return zeichnungsbedarfe.anlegen(request, datei, revision, akteurId);
    }

    @PutMapping("/{id}")
    public EinkaufBedarfDto.Response aktualisieren(@PathVariable Long id,
            @RequestBody EinkaufBedarfDto.Update request, Authentication authentication) {
        Long akteurId = berechtigungService.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return bedarfService.aktualisieren(id, request, akteurId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Eingabefehler> handleEingabefehler(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new Eingabefehler(exception.getMessage(),
                List.of(new Feldfehler("request", exception.getMessage()))));
    }

    public record Eingabefehler(String message, List<Feldfehler> fieldErrors) {}
    public record Feldfehler(String field, String message) {}
}
