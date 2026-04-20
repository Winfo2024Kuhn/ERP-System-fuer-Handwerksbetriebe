package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageStatus;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageAngebotEintragenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageVergleichDto;
import org.example.kalkulationsprogramm.mapper.PreisanfrageMapper;
import org.example.kalkulationsprogramm.service.BestellungPdfService;
import org.example.kalkulationsprogramm.service.PreisanfrageAngebotsExtraktionService;
import org.example.kalkulationsprogramm.service.PreisanfrageAngebotsExtraktionService.ExtraktionsErgebnis;
import org.example.kalkulationsprogramm.service.PreisanfrageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * REST-Endpoints fuer Preisanfragen an mehrere Lieferanten.
 * <p>
 * Security: Greift auf die globale {@code SecurityConfig}-Kette (Session-Auth
 * fuer {@code /api/**}). Entities verlassen den Service NUR via DTOs.
 */
@RestController
@RequestMapping("/api/preisanfragen")
@AllArgsConstructor
public class PreisanfrageController {

    private final PreisanfrageService preisanfrageService;
    private final PreisanfrageMapper preisanfrageMapper;
    private final BestellungPdfService bestellungPdfService;
    private final PreisanfrageAngebotsExtraktionService angebotsExtraktionService;

    /** Neue Preisanfrage anlegen. */
    @PostMapping
    public ResponseEntity<PreisanfrageResponseDto> erstellen(
            @Valid @RequestBody PreisanfrageErstellenDto dto) {
        try {
            Preisanfrage pa = preisanfrageService.erstellen(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(preisanfrageMapper.toResponseDto(pa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** An alle vorbereiteten Lieferanten versenden. */
    @PostMapping("/{id}/versenden")
    public ResponseEntity<PreisanfrageResponseDto> versendeAnAlleLieferanten(@PathVariable Long id) {
        try {
            preisanfrageService.versendeAnAlleLieferanten(id);
            return ResponseEntity.ok(
                    preisanfrageMapper.toResponseDto(preisanfrageService.findeById(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** Einzelnen Lieferanten versenden (Retry). */
    @PostMapping("/lieferant/{palId}/versenden")
    public ResponseEntity<Void> versendeEinzelnenLieferanten(@PathVariable Long palId) {
        try {
            preisanfrageService.versendeAnEinzelnenLieferanten(palId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /**
     * Liste aller Preisanfragen, optional nach Status gefiltert.
     * Ungueltige Status-Werte werden als "kein Filter" behandelt (keine 500).
     */
    @GetMapping
    public List<PreisanfrageResponseDto> liste(@RequestParam(required = false) String status) {
        PreisanfrageStatus filter = parseStatus(status);
        return preisanfrageService.listeAlle(filter).stream()
                .map(preisanfrageMapper::toResponseDto)
                .toList();
    }

    /** Detail einer einzelnen Preisanfrage. */
    @GetMapping("/{id}")
    public ResponseEntity<PreisanfrageResponseDto> detail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    preisanfrageMapper.toResponseDto(preisanfrageService.findeById(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** Vergleichs-Matrix fuer das UI (Positionen x Lieferanten). */
    @GetMapping("/{id}/vergleich")
    public ResponseEntity<PreisanfrageVergleichDto> vergleich(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(preisanfrageService.getVergleich(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** PDF-Download pro Lieferant (Token im PDF-Kopf). */
    @GetMapping("/lieferant/{palId}/pdf")
    public ResponseEntity<Resource> pdfForLieferant(@PathVariable Long palId) {
        if (palId == null || palId <= 0) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path pdf = bestellungPdfService.generatePdfForPreisanfrage(palId);
            Resource res = new InputStreamResource(Files.newInputStream(pdf));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=preisanfrage-" + palId + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Reason", "PDF konnte nicht erzeugt werden")
                    .build();
        }
    }

    /**
     * Extrahiert Angebotspreise aus Antwort-PDFs per Gemini (PDF-Direkt).
     * Laeuft synchron; pro Lieferant ein Gemini-Call. Bestehende
     * {@link PreisanfrageAngebot}-Zeilen werden NICHT ueberschrieben.
     */
    @PostMapping("/{id}/angebote/extrahieren")
    public ResponseEntity<ExtraktionsErgebnis> extrahiereAngebote(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(angebotsExtraktionService.extrahiereFuerPreisanfrage(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** Angebotspreis manuell eintragen / aktualisieren. */
    @PostMapping("/angebote")
    public ResponseEntity<Long> eintragen(@Valid @RequestBody PreisanfrageAngebotEintragenDto dto) {
        try {
            PreisanfrageAngebot angebot = preisanfrageService.eintragen(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(angebot.getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /**
     * Vergibt den Auftrag an den Gewinner-Lieferanten (routet
     * {@code ArtikelInProjekt}-Zeilen um; siehe Service-Javadoc).
     */
    @PostMapping("/{id}/vergeben/{palId}")
    public ResponseEntity<PreisanfrageResponseDto> vergeben(
            @PathVariable Long id,
            @PathVariable Long palId) {
        try {
            preisanfrageService.vergebeAuftrag(id, palId);
            return ResponseEntity.ok(
                    preisanfrageMapper.toResponseDto(preisanfrageService.findeById(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    /** Preisanfrage abbrechen (Soft-Delete, Status ABGEBROCHEN). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> abbrechen(@PathVariable Long id) {
        try {
            preisanfrageService.abbrechen(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    private static PreisanfrageStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PreisanfrageStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
