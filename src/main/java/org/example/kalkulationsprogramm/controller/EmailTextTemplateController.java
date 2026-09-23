package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.EmailTextTemplateKategorie;
import org.example.kalkulationsprogramm.dto.Email.EmailTextTemplateDto;
import org.example.kalkulationsprogramm.service.EmailTextTemplateKategorien;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVorlagenService;
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

    // Single Source of Truth: Doc-Typen kommen aus dem Dokumenttyp-Enum.
    // ZEICHNUNG existiert nicht im Enum, wird aber als E-Mail-Vorlage benötigt
    // (Versand der ersten Entwurfs-PDF an den Kunden) und daher manuell ergänzt.
    // WEBSITE_ANFRAGE_BESTAETIGUNG ist die automatische Bestätigungsmail an
    // Leads aus dem Funnel der Marketing-Webseite — kein Geschaeftsdokument,
    // aber per E-Mail-Vorlage genauso editierbar.
    private static final List<Map<String, String>> DOKUMENT_TYPEN = buildDokumentTypen();

    private static List<Map<String, String>> buildDokumentTypen() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Dokumenttyp d : Dokumenttyp.values()) {
            String label = switch (d) {
                case ANGEBOT -> "Anfrage / Angebot";
                default -> d.getLabel();
            };
            list.add(buildOption(d.name(), label));
        }
        list.add(buildOption("ZEICHNUNG", "Zeichnung / Entwurf"));
        list.add(buildOption("WEBSITE_ANFRAGE_BESTAETIGUNG", "Webseite — Anfragebestätigung"));
        list.add(buildOption("EINKAUF_ANFRAGE", "Einkauf — Lieferantenanfrage"));
        list.add(buildOption("EINKAUF_BESTELLUNG", "Einkauf — Bestellung"));
        list.add(buildOption("EINKAUF_DIREKTBESTELLUNG", "Einkauf — Direktbestellung"));
        list.add(buildOption("EINKAUF_NACHFRAGE", "Einkauf — Nachfrage zum Angebot"));
        list.add(buildOption("EINKAUF_ZEUGNIS_NACHFORDERUNG", "Einkauf — Werkstoffzeugnis nachfordern"));
        list.add(buildOption("EINKAUF_BESTAETIGUNG_NACHFRAGE", "Einkauf — Auftragsbestätigung nachfragen"));
        list.add(buildOption("EINKAUF_LIEFERUNG_NACHFRAGE", "Einkauf — Liefertermin nachfragen"));
        return List.copyOf(list);
    }

    private static Map<String, String> buildOption(String value, String label) {
        // LinkedHashMap, damit die Reihenfolge der Keys im JSON deterministisch
        // ist — Map.of() ist intern unsortiert, was beim Snapshot-Vergleich in
        // den Controller-Tests irritierende Diff-Treffer erzeugen kann.
        Map<String, String> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        EmailTextTemplateKategorie kat = EmailTextTemplateKategorien.kategorieFuer(value);
        option.put("kategorie", kat.name());
        option.put("kategorieLabel", kat.getLabel());
        return option;
    }

    private static final List<Map<String, String>> PLACEHOLDERS = List.of(
            Map.of("token", "{{ANREDE}}", "label", "Anrede (z. B. Sehr geehrter Herr Müller)"),
            Map.of("token", "{{KUNDENNAME}}", "label", "Kundenname (Firma / Name des Kunden)"),
            Map.of("token", "{{ANSPRECHPARTNER}}", "label", "Ansprechpartner (Person beim Kunden)"),
            Map.of("token", "{{BAUVORHABEN}}", "label", "Bauvorhaben"),
            Map.of("token", "{{PROJEKTNUMMER}}", "label", "Projektnummer"),
            Map.of("token", "{{DOKUMENTNUMMER}}", "label", "Dokumentnummer (Rechnung/Auftrag/Anfrage)"),
            Map.of("token", "{{RECHNUNGSDATUM}}", "label", "Rechnungsdatum"),
            Map.of("token", "{{FAELLIGKEITSDATUM}}", "label", "Fälligkeitsdatum"),
            Map.of("token", "{{BETRAG}}", "label", "Betrag (formatiert)"),
            Map.of("token", "{{BENUTZER}}", "label", "Sachbearbeiter / Benutzer"),
            Map.of("token", "{{REVIEW_LINK}}", "label", "Google-Bewertungs-Link"),
            // Bankverbindung der Firma (aus den Firmeneinstellungen).
            // Wird vom EmailTextTemplateService automatisch in jeden Render-
            // Kontext eingespielt, daher in allen Vorlagen verfuegbar.
            Map.of("token", "{{BANK}}", "label", "Bank (aus Firmeneinstellungen)"),
            Map.of("token", "{{IBAN}}", "label", "IBAN (aus Firmeneinstellungen)"),
            Map.of("token", "{{BIC}}", "label", "BIC (aus Firmeneinstellungen)"),
            // Webseiten-Lead-Bestätigung
            Map.of("token", "{{NACHRICHT}}", "label", "Nachricht aus dem Webseiten-Funnel"),
            Map.of("token", "{{ANFRAGE_DATUM}}", "label", "Anfrage-Datum (Webseite)"),
            Map.of("token", "{{ANFRAGENUMMER}}", "label", "Anfrage-Nummer (Webseite)"));

    private final EmailTextTemplateService service;
    private final EinkaufVorlagenService einkaufVorlagenService;

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

    @GetMapping("/placeholders/{dokumentTyp}")
    public ResponseEntity<List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Platzhalter>>
            placeholders(@PathVariable String dokumentTyp) {
        try {
            return ResponseEntity.ok(einkaufVorlagenService.placeholders(dokumentTyp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
