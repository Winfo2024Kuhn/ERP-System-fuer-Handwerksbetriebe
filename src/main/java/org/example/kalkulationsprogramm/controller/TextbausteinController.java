package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Textbaustein.DokumenttypGruppeDto;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinPlatzhalterDto;
import org.example.kalkulationsprogramm.service.TextbausteinKatalogService;
import org.example.kalkulationsprogramm.service.TextbausteinService;
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
@RequestMapping("/api/textbausteine")
@RequiredArgsConstructor
public class TextbausteinController {

    private final TextbausteinService textbausteinService;
    private final TextbausteinKatalogService katalogService;

    @GetMapping
    public ResponseEntity<List<TextbausteinDto>> list() {
        return ResponseEntity.ok(textbausteinService.list().stream().map(TextbausteinDto::fromEntity).toList());
    }

    @PostMapping
    public ResponseEntity<TextbausteinDto> create(@Valid @RequestBody TextbausteinDto dto) {
        return ResponseEntity.ok(TextbausteinDto.fromEntity(textbausteinService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TextbausteinDto> update(@PathVariable Long id, @Valid @RequestBody TextbausteinDto dto) {
        return ResponseEntity.ok(TextbausteinDto.fromEntity(textbausteinService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        textbausteinService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Platzhalter mit Bezeichnung, Beispielwert und Gruppe, in Anzeige-Reihenfolge. */
    @GetMapping("/placeholders")
    public ResponseEntity<List<TextbausteinPlatzhalterDto>> placeholders() {
        return ResponseEntity.ok(katalogService.platzhalter());
    }

    /** Dokumentarten nach Überschriften gruppiert, für Filter und Auswahl der Textbaustein-Seite. */
    @GetMapping("/dokumenttyp-gruppen")
    public ResponseEntity<List<DokumenttypGruppeDto>> dokumenttypGruppen() {
        return ResponseEntity.ok(katalogService.dokumenttypGruppen());
    }
}
