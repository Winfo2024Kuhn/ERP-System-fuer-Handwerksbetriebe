package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.Email.EmailTextTemplateDto;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email-textvorlagen")
@RequiredArgsConstructor
public class EmailTextTemplateController {

    private static final List<Map<String, String>> DOKUMENT_TYPEN = List.of(
            Map.of("value", "RECHNUNG", "label", "Rechnung"),
            Map.of("value", "TEILRECHNUNG", "label", "Teilrechnung"),
            Map.of("value", "SCHLUSSRECHNUNG", "label", "Schlussrechnung"),
            Map.of("value", "ABSCHLAGSRECHNUNG", "label", "Abschlagsrechnung"),
            Map.of("value", "MAHNUNG", "label", "Mahnung"),
            Map.of("value", "ANGEBOT", "label", "Anfrage / Angebot"),
            Map.of("value", "AUFTRAGSBESTAETIGUNG", "label", "Auftragsbestätigung"),
            Map.of("value", "ZEICHNUNG", "label", "Zeichnung / Entwurf"));

    private static final List<Map<String, String>> PLACEHOLDERS = List.of(
            Map.of("token", "{{ANREDE}}", "label", "Anrede (z. B. Sehr geehrter Herr Müller)"),
            Map.of("token", "{{KUNDENNAME}}", "label", "Kundenname"),
            Map.of("token", "{{BAUVORHABEN}}", "label", "Bauvorhaben"),
            Map.of("token", "{{PROJEKTNUMMER}}", "label", "Projektnummer"),
            Map.of("token", "{{DOKUMENTNUMMER}}", "label", "Dokumentnummer (Rechnung/Auftrag/Anfrage)"),
            Map.of("token", "{{RECHNUNGSDATUM}}", "label", "Rechnungsdatum"),
            Map.of("token", "{{FAELLIGKEITSDATUM}}", "label", "Fälligkeitsdatum"),
            Map.of("token", "{{BETRAG}}", "label", "Betrag (formatiert)"),
            Map.of("token", "{{BENUTZER}}", "label", "Sachbearbeiter / Benutzer"),
            Map.of("token", "{{REVIEW_LINK}}", "label", "Google-Bewertungs-Link"));

    private final EmailTextTemplateService service;

    @GetMapping
    public ResponseEntity<List<EmailTextTemplateDto>> list() {
        List<EmailTextTemplateDto> result = service.list().stream()
                .map(EmailTextTemplateDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailTextTemplateDto> get(@PathVariable Long id) {
        try {
            EmailTextTemplate entity = service.get(id);
            return ResponseEntity.ok(EmailTextTemplateDto.fromEntity(entity));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<EmailTextTemplateDto> create(@Valid @RequestBody EmailTextTemplateDto dto) {
        EmailTextTemplate saved = service.create(dto);
        return ResponseEntity.ok(EmailTextTemplateDto.fromEntity(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmailTextTemplateDto> update(@PathVariable Long id, @Valid @RequestBody EmailTextTemplateDto dto) {
        try {
            EmailTextTemplate saved = service.update(id, dto);
            return ResponseEntity.ok(EmailTextTemplateDto.fromEntity(saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dokumenttypen")
    public ResponseEntity<List<Map<String, String>>> dokumenttypen() {
        return ResponseEntity.ok(DOKUMENT_TYPEN);
    }

    @GetMapping("/placeholders")
    public ResponseEntity<List<Map<String, String>>> placeholders() {
        return ResponseEntity.ok(PLACEHOLDERS);
    }
}
