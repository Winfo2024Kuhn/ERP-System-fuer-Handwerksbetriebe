package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST Controller für Ausgangs-Geschäftsdokumente.
 * Unterstützt: Erstellen, Aktualisieren, Buchen, Stornieren, Export.
 */
@RestController
@RequestMapping("/api/ausgangs-dokumente")
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentController {

    private static final Logger log = LoggerFactory.getLogger(AusgangsGeschaeftsDokumentController.class);

    private final AusgangsGeschaeftsDokumentService service;

    /**
     * Einzelnes Dokument abrufen.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AusgangsGeschaeftsDokumentResponseDto> getById(@PathVariable Long id) {
        AusgangsGeschaeftsDokumentResponseDto dto = service.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Alle Dokumente für ein Projekt abrufen.
     */
    @GetMapping("/projekt/{projektId}")
    public ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> getByProjekt(@PathVariable Long projektId) {
        return ResponseEntity.ok(service.findByProjekt(projektId));
    }

    /**
     * Alle Dokumente für ein Anfrage abrufen.
     */
    @GetMapping("/anfrage/{anfrageId}")
    public ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> getByAnfrage(@PathVariable Long anfrageId) {
        return ResponseEntity.ok(service.findByAnfrage(anfrageId));
    }

    /**
     * Neues Dokument erstellen.
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody AusgangsGeschaeftsDokumentErstellenDto dto) {
        try {
            AusgangsGeschaeftsDokument created = service.erstellen(dto);
            var response = service.findById(created.getId());
            response.setAutoZugewieseneWps(created.getAutoZugewieseneWps());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument aktualisieren (nur wenn nicht gebucht).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody AusgangsGeschaeftsDokumentUpdateDto dto) {
        try {
            AusgangsGeschaeftsDokument updated = service.aktualisieren(id, dto);
            var response = service.findById(updated.getId());
            response.setAutoZugewieseneWps(updated.getAutoZugewieseneWps());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Fehler beim Aktualisieren von Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument buchen (sperrt für Bearbeitung).
     * Wird normalerweise beim PDF-Export aufgerufen.
     */
    @PostMapping("/{id}/buchen")
    public ResponseEntity<?> buchen(@PathVariable Long id) {
        try {
            AusgangsGeschaeftsDokument result = service.buchen(id);
            return ResponseEntity.ok(service.findById(result.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument nach E-Mail-Versand buchen (GoBD-konform).
     * Setzt Versanddatum und sperrt das Dokument.
     */
    @PostMapping("/{id}/email-versendet")
    public ResponseEntity<?> emailVersendet(@PathVariable Long id) {
        try {
            service.buchenNachEmailVersand(id);
            return ResponseEntity.ok(service.findById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PDF für ein gebuchtes Dokument speichern.
     * Wird vom Frontend nach PDF-Generierung aufgerufen, damit der Offene-Posten-Eintrag
     * direkt auf die PDF-Datei verweist statt auf den Document-Editor.
     */
    @PostMapping("/{id}/pdf-speichern")
    public ResponseEntity<?> pdfSpeichern(@PathVariable Long id, @RequestBody byte[] pdfBytes) {
        try {
            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.badRequest().body("Keine PDF-Daten erhalten");
            }
            service.speicherePdfFuerDokument(id, pdfBytes);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Fehler beim Speichern der PDF für Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PDF für ein AusgangsGeschaeftsDokument abrufen (Fallback für alte Einträge).
     * Gibt 404 zurück wenn keine PDF gespeichert ist.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> getPdf(@PathVariable Long id) {
        return ResponseEntity.status(404).body("PDF nicht verfügbar. Bitte exportieren Sie das Dokument erneut.");
    }

    /**
     * Dokument stornieren.
     * Erstellt ein Storno-Gegendokument.
     */
    @PostMapping("/{id}/storno")
    public ResponseEntity<?> stornieren(@PathVariable Long id) {
        try {
            AusgangsGeschaeftsDokument storno = service.stornieren(id);
            return ResponseEntity.ok(service.findById(storno.getId()));
        } catch (RuntimeException e) {
            log.error("Fehler beim Stornieren von Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument löschen.
     * Alle nicht gebuchten Dokumente dürfen mit einer Begründung gelöscht werden.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam String begruendung) {
        try {
            AusgangsGeschaeftsDokumentResponseDto dto = service.findById(id);
            if (dto == null) {
                return ResponseEntity.notFound().build();
            }

            service.loeschen(id, begruendung);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Abrechnungsverlauf ---

    /**
     * Abrechnungsverlauf für ein Basisdokument abrufen.
     * Zeigt alle bereits erstellten Rechnungen und den verbleibenden Restbetrag.
     */
    @GetMapping("/{id}/abrechnungsverlauf")
    public ResponseEntity<?> getAbrechnungsverlauf(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getAbrechnungsverlauf(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // TODO: PDF/ZUGFeRD/XML Export Endpoints werden später implementiert
    // @PostMapping("/{id}/export/pdf")
    // @PostMapping("/{id}/export/zugferd")
    // @PostMapping("/{id}/export/xml")
}
