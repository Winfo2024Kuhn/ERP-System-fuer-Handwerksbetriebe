package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.service.TextbausteinService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;

@RestController
@RequestMapping("/api/textbausteine")
@RequiredArgsConstructor
public class TextbausteinController {

    private final TextbausteinService textbausteinService;

    private static final Map<String, String> PLACEHOLDER_BESCHREIBUNG = Map.ofEntries(
            Map.entry("{{DOKUMENTNUMMER}}", "Aktuelle Dokumentnummer"),
            Map.entry("{{PROJEKTNUMMER}}", "Projektnummer"),
            Map.entry("{{Anrede}}", "Anrede des Kunden"),
            Map.entry("{{KUNDENNAME}}", "Kundenname"),
            Map.entry("{{Ansprechpartner}}", "Ansprechpartner des Kunden"),
            Map.entry("{{KUNDENNUMMER}}", "Kundennummer"),
            Map.entry("{{KUNDENADRESSE}}", "Kundenadresse mehrzeilig"),
            Map.entry("{{BAUVORHABEN}}", "Bauvorhaben / Projektname"),
            Map.entry("{{DATUM}}", "Heutiges Datum"),
            Map.entry("{{SEITENZAHL}}", "Seitenangabe"),
            Map.entry("{{DOKUMENTTYP}}", "Dokumenttyp (z. B. Rechnung)"),
            Map.entry("{{ZAHLUNGSZIEL}}", "Zahlungsziel als Datum"),
            Map.entry("{{ZAHLUNGSZIEL_TAGE}}", "Zahlungsziel des Kunden in Tagen"),
            Map.entry("{{BEZUGSDOKUMENTNUMMER}}", "Dokumentnummer des Bezugsdokuments (Vorgänger)"),
            Map.entry("{{BEZUGSDOKUMENTDATUM}}", "Datum des Bezugsdokuments (Vorgänger)"),
            Map.entry("{{BEZUGSDOKUMENTTYP}}", "Typ des Bezugsdokuments (z. B. Angebot)")
    );

    @GetMapping
    public ResponseEntity<List<TextbausteinDto>> list(@RequestParam(value = "typ", required = false) String typ) {
        List<Textbaustein> items = textbausteinService.list(typ);
        List<TextbausteinDto> result = items.stream().map(TextbausteinDto::fromEntity).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TextbausteinDto> get(@PathVariable Long id) {
        try {
            Textbaustein entity = textbausteinService.get(id);
            return ResponseEntity.ok(TextbausteinDto.fromEntity(entity));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<TextbausteinDto> create(@Valid @RequestBody TextbausteinDto dto) {
        Textbaustein saved = textbausteinService.create(dto);
        return ResponseEntity.ok(TextbausteinDto.fromEntity(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TextbausteinDto> update(@PathVariable Long id, @Valid @RequestBody TextbausteinDto dto) {
        try {
            Textbaustein saved = textbausteinService.update(id, dto);
            return ResponseEntity.ok(TextbausteinDto.fromEntity(saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        textbausteinService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/placeholders")
    public ResponseEntity<List<Map<String, String>>> placeholders() {
        List<Map<String, String>> list = PLACEHOLDER_BESCHREIBUNG.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Map.of("token", e.getKey(), "beschreibung", e.getValue()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/typen")
    public ResponseEntity<List<String>> typen() {
        return ResponseEntity.ok(
                List.of(TextbausteinTyp.VORTEXT.name(), TextbausteinTyp.NACHTEXT.name(), TextbausteinTyp.ZAHLUNGSZIEL.name(), TextbausteinTyp.FREITEXT.name())
                        .stream()
                        .map(t -> StringUtils.capitalize(t.toLowerCase()))
                        .toList()
        );
    }

    @GetMapping("/dokumenttypen")
    public ResponseEntity<List<String>> dokumenttypen() {
        List<String> labels = java.util.Arrays.stream(Dokumenttyp.values())
                .map(Dokumenttyp::getLabel)
                .toList();
        return ResponseEntity.ok(labels);
    }
}
