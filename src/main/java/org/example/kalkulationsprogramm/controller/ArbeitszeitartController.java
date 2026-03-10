package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Arbeitszeitart.ArbeitszeitartCreateDto;
import org.example.kalkulationsprogramm.dto.Arbeitszeitart.ArbeitszeitartDto;
import org.example.kalkulationsprogramm.service.ArbeitszeitartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-Controller für Arbeitszeitarten (Stundensätze).
 * 
 * Verwendung: Auswahl im DocumentEditor für Stundenabrechnung.
 * 
 * WICHTIG zum Snapshot-Prinzip:
 * Bei Dokumenterstellung werden die aktuellen Werte (Bezeichnung, Stundensatz)
 * als Snapshot in positionenJson gespeichert. Spätere Änderungen an den
 * Stammdaten haben keinen Einfluss auf bereits erstellte Dokumente.
 */
@RestController
@RequestMapping("/api/arbeitszeitarten")
@RequiredArgsConstructor
public class ArbeitszeitartController {

    private final ArbeitszeitartService service;

    /**
     * GET /api/arbeitszeitarten - Alle aktiven Arbeitszeitarten (für Dokumenterstellung)
     */
    @GetMapping
    public List<ArbeitszeitartDto> getAll() {
        return service.findAllAktiv();
    }

    /**
     * GET /api/arbeitszeitarten/alle - Alle Arbeitszeitarten inkl. inaktive (für Verwaltung)
     */
    @GetMapping("/alle")
    public List<ArbeitszeitartDto> getAllInclInactive() {
        return service.findAll();
    }

    /**
     * GET /api/arbeitszeitarten/{id}
     */
    @GetMapping("/{id}")
    public ArbeitszeitartDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * POST /api/arbeitszeitarten - Neue Arbeitszeitart erstellen
     */
    @PostMapping
    public ArbeitszeitartDto create(@Valid @RequestBody ArbeitszeitartCreateDto dto) {
        return service.create(dto);
    }

    /**
     * PUT /api/arbeitszeitarten/{id} - Arbeitszeitart aktualisieren
     */
    @PutMapping("/{id}")
    public ArbeitszeitartDto update(@PathVariable Long id, @Valid @RequestBody ArbeitszeitartCreateDto dto) {
        return service.update(id, dto);
    }

    /**
     * DELETE /api/arbeitszeitarten/{id} - Arbeitszeitart löschen
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/arbeitszeitarten/{id}/deaktivieren - Arbeitszeitart deaktivieren (empfohlen statt Löschen)
     */
    @PostMapping("/{id}/deaktivieren")
    public ResponseEntity<Void> deaktivieren(@PathVariable Long id) {
        service.deaktivieren(id);
        return ResponseEntity.noContent().build();
    }
}
