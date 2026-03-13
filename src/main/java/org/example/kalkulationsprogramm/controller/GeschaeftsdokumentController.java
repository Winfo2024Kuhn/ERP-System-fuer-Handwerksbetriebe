package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Geschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Zahlung;
import org.example.kalkulationsprogramm.dto.Geschaeftsdokument.*;
import org.example.kalkulationsprogramm.service.GeschaeftsdokumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller für Geschäftsdokumente.
 * Unterstützt Dokumenten-Workflow: Anfrage → Auftragsbestätigung → Rechnung(en)
 */
@RestController
@RequestMapping("/api/geschaeftsdokumente")
@RequiredArgsConstructor
public class GeschaeftsdokumentController {

    private final GeschaeftsdokumentService service;

    /**
     * Alle Dokumente abrufen.
     */
    @GetMapping
    public ResponseEntity<List<GeschaeftsdokumentResponseDto>> getAll() {
        // TODO: Pagination hinzufügen
        return ResponseEntity.ok(service.findByProjekt(null)); // Alle Dokumente (Workaround)
    }

    /**
     * Einzelnes Dokument abrufen.
     */
    @GetMapping("/{id}")
    public ResponseEntity<GeschaeftsdokumentResponseDto> getById(@PathVariable Long id) {
        GeschaeftsdokumentResponseDto dto = service.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Abschluss-Informationen für ein Dokument abrufen.
     * Enthält Netto/MwSt/Brutto, Vorgänger-Referenzen, bisherige Zahlungen, offener
     * Betrag.
     */
    @GetMapping("/{id}/abschluss")
    public ResponseEntity<AbschlussInfoDto> getAbschluss(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.berechneAbschluss(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Dokumente eines Projekts abrufen.
     */
    @GetMapping("/projekt/{projektId}")
    public ResponseEntity<List<GeschaeftsdokumentResponseDto>> getByProjekt(@PathVariable Long projektId) {
        return ResponseEntity.ok(service.findByProjekt(projektId));
    }

    /**
     * Neues Dokument erstellen.
     */
    @PostMapping
    public ResponseEntity<GeschaeftsdokumentResponseDto> create(@RequestBody GeschaeftsdokumentErstellenDto dto) {
        Geschaeftsdokument created = service.erstellen(dto);
        return ResponseEntity.ok(service.findById(created.getId()));
    }

    /**
     * Dokument in anderen Typ konvertieren.
     * z.B. Anfrage → Auftragsbestätigung, AB → Rechnung
     */
    @PostMapping("/{id}/konvertieren")
    public ResponseEntity<GeschaeftsdokumentResponseDto> konvertieren(
            @PathVariable Long id,
            @RequestParam String neuerDokumenttyp) {
        try {
            Geschaeftsdokument neues = service.konvertieren(id, neuerDokumenttyp);
            return ResponseEntity.ok(service.findById(neues.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Zahlung zu einem Dokument erfassen.
     */
    @PostMapping("/{id}/zahlungen")
    public ResponseEntity<ZahlungDto> zahlungErfassen(
            @PathVariable Long id,
            @RequestBody ZahlungErstellenDto dto) {
        try {
            Zahlung zahlung = service.zahlungErfassen(id, dto);

            ZahlungDto response = new ZahlungDto();
            response.setId(zahlung.getId());
            response.setZahlungsdatum(zahlung.getZahlungsdatum());
            response.setBetrag(zahlung.getBetrag());
            response.setZahlungsart(zahlung.getZahlungsart());
            response.setVerwendungszweck(zahlung.getVerwendungszweck());
            response.setNotiz(zahlung.getNotiz());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

}
