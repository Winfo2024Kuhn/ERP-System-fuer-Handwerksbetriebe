package org.example.kalkulationsprogramm.controller;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.*;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAntwortZuordnungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufKommunikationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf")
public class EinkaufKommunikationController {
    private final EinkaufKommunikationService kommunikation;
    private final EinkaufAntwortZuordnungService zuordnung;
    private final EinkaufBerechtigungService rechte;
    private final EmailImportService emailImport;
    private final org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService dateien;

    public EinkaufKommunikationController(EinkaufKommunikationService kommunikation, EinkaufAntwortZuordnungService zuordnung,
            EinkaufBerechtigungService rechte, EmailImportService emailImport,
            org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService dateien) {
        this.kommunikation = kommunikation; this.zuordnung = zuordnung; this.rechte = rechte; this.emailImport = emailImport; this.dateien = dateien;
    }

    @GetMapping("/anfragen/{id}/lieferanten/{beteiligungId}/vorschau")
    public Vorschau vorschau(@PathVariable Long id, @PathVariable Long beteiligungId,
            @RequestParam Long templateId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.ANFRAGE_SENDEN);
        return kommunikation.vorschau(id, beteiligungId, templateId);
    }

    @PostMapping("/anfragen/{id}/lieferanten/{beteiligungId}/senden")
    public VersandErgebnis senden(@PathVariable Long id, @PathVariable Long beteiligungId,
            @RequestBody Freigabe request, Authentication authentication) {
        Long akteur = rechte.verlange(authentication, EinkaufBerechtigung.ANFRAGE_SENDEN);
        return kommunikation.senden(id, beteiligungId, request, akteur);
    }

    @GetMapping("/pdf-vorschau/{dateiId}")
    public ResponseEntity<Resource> pdfVorschau(@PathVariable Long dateiId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        Resource resource = dateien.ladePdfSnapshot(dateiId, authentication);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("einkauf-anfrage.pdf", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(resource);
    }

    @PostMapping("/mail/{emailId}/zuordnung")
    public Zuordnungsergebnis bestaetigen(
            @PathVariable Long emailId, @RequestBody ZuordnungRequest request, Authentication authentication) {
        Long akteur = rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        if (request == null) throw new IllegalArgumentException("Die Zuordnung fehlt.");
        return zuordnung.bestaetigen(emailId, request.typ(), request.vorgangId(), request.beteiligungId(),
                request.revisionId(), request.begruendung(), akteur);
    }

    @PostMapping("/mail/{emailId}/zuordnung/ermitteln")
    public Zuordnungsergebnis ermitteln(
            @PathVariable Long emailId, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return zuordnung.zuordnen(emailId);
    }

    @PostMapping("/mail/abruf")
    public AbrufErgebnis abrufen(Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return new AbrufErgebnis(emailImport.doImport("EINKAUF"));
    }

    @GetMapping("/{typ}/{id}/verlauf")
    public Page<NachrichtDto> verlauf(@PathVariable String typ, @PathVariable Long id,
            Pageable pageable, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return kommunikation.verlauf(typ, id, pageable);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Eingabefehler> handleEingabefehler(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new Eingabefehler(exception.getMessage(),
                List.of(new Feldfehler("request", exception.getMessage()))));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Eingabefehler> handleKonflikt(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Eingabefehler(exception.getMessage(), List.of()));
    }
    public record AbrufErgebnis(int importierteNachrichten) {}
    public record Eingabefehler(String message, List<Feldfehler> fieldErrors) {}
    public record Feldfehler(String field, String message) {}
}
