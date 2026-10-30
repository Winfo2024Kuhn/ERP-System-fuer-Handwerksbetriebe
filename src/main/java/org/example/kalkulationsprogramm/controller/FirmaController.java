package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Feiertag.FeiertageVorschauDto;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.service.FeiertageVorschauService;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller für Firmenstammdaten und Steuerberater (Kostenstellen: {@link KostenstelleController}).
 */
@RestController
@RequestMapping("/api/firma")
@RequiredArgsConstructor
public class FirmaController {

    private final FirmeninformationService firmeninformationService;
    private final SteuerberaterKontaktService steuerberaterKontaktService;
    private final FeiertageVorschauService feiertageVorschauService;

    // ==================== FIRMENINFORMATION ====================

    @GetMapping
    public ResponseEntity<FirmeninformationDto> getFirmeninformation() {
        return ResponseEntity.ok(firmeninformationService.getFirmeninformation());
    }

    @PutMapping
    public ResponseEntity<FirmeninformationDto> speichernFirmeninformation(@RequestBody FirmeninformationDto dto) {
        return ResponseEntity.ok(firmeninformationService.speichern(dto));
    }

    /**
     * Vorschlag für das Bundesland aus der Postleitzahl und die Feiertage, die dort gelten
     * (Issue #222). Speichert nichts.
     */
    @GetMapping("/feiertage-vorschau")
    public ResponseEntity<FeiertageVorschauDto> feiertageVorschau(
            @RequestParam(required = false) String plz,
            @RequestParam(required = false) String bundesland,
            @RequestParam(required = false) Integer jahr) {
        return ResponseEntity.ok(feiertageVorschauService.vorschau(plz, bundesland, jahr));
    }

    // ==================== FIRMENLOGO ====================

    /**
     * Laedt das Firmenlogo hoch (multipart). Whitelist: PNG, JPEG, WebP.
     * Der Client-Dateiname wird bewusst ignoriert – wir speichern unter
     * {@code logo.<ext>}, um Pfad-Traversal komplett auszuschliessen.
     */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLogo(@RequestParam("datei") MultipartFile datei) {
        try {
            FirmeninformationDto dto = firmeninformationService.speichereLogoDatei(datei);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Speichern fehlgeschlagen"));
        }
    }

    /**
     * Liefert das aktuell hinterlegte Firmenlogo als Binary-Response.
     * 404, falls kein Logo gepflegt ist.
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> downloadLogo() {
        byte[] bytes = firmeninformationService.loadLogoBytes();
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = firmeninformationService.ermittleLogoContentType();
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(bytes);
    }

    @DeleteMapping("/logo")
    public ResponseEntity<FirmeninformationDto> deleteLogo() {
        return ResponseEntity.ok(firmeninformationService.loescheLogoDatei());
    }

    // ==================== STEUERBERATER ====================

    @GetMapping("/steuerberater")
    public ResponseEntity<List<SteuerberaterKontaktDto>> getSteuerberater() {
        return ResponseEntity.ok(steuerberaterKontaktService.findAll());
    }

    @GetMapping("/steuerberater/{id}")
    public ResponseEntity<SteuerberaterKontaktDto> getSteuerberaterById(@PathVariable Long id) {
        SteuerberaterKontaktDto dto = steuerberaterKontaktService.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/steuerberater")
    public ResponseEntity<SteuerberaterKontaktDto> erstelleSteuerberater(@RequestBody SteuerberaterKontaktDto dto) {
        dto.setId(null);
        return ResponseEntity.ok(steuerberaterKontaktService.speichern(dto));
    }

    @PutMapping("/steuerberater/{id}")
    public ResponseEntity<SteuerberaterKontaktDto> aktualisiereSteuerberater(
            @PathVariable Long id, 
            @RequestBody SteuerberaterKontaktDto dto) {
        dto.setId(id);
        return ResponseEntity.ok(steuerberaterKontaktService.speichern(dto));
    }

    @DeleteMapping("/steuerberater/{id}")
    public ResponseEntity<Void> loescheSteuerberater(@PathVariable Long id) {
        steuerberaterKontaktService.loeschen(id);
        return ResponseEntity.noContent().build();
    }
}
