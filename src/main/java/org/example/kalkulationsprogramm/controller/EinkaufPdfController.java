package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/einkauf")
@RequiredArgsConstructor
public class EinkaufPdfController {
    private final EinkaufPdfService pdfService;
    private final EinkaufBerechtigungService berechtigungService;

    @GetMapping(value = "/lagerentnahmen/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> entnahmeliste(@RequestParam List<Long> bedarfIds,
            Authentication authentication) {
        berechtigungService.verlange(authentication, EinkaufBerechtigung.LESEN);
        Resource pdf = pdfService.entnahmeliste(bedarfIds);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("lagerentnahme.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Eingabefehler> handleEingabefehler(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Eingabefehler(exception.getMessage(),
                List.of(new Feldfehler("bedarfIds", exception.getMessage()))));
    }

    public record Eingabefehler(String message, List<Feldfehler> fieldErrors) {}
    public record Feldfehler(String field, String message) {}
}
