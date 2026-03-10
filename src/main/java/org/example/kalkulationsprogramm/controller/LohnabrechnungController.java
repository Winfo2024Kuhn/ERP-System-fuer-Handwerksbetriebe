package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.LohnabrechnungDto;
import org.example.kalkulationsprogramm.service.LohnabrechnungService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/**
 * REST Controller für Lohnabrechnungen.
 */
@RestController
@RequestMapping("/api/lohnabrechnungen")
@RequiredArgsConstructor
public class LohnabrechnungController {

    private final LohnabrechnungService lohnabrechnungService;

    @org.springframework.beans.factory.annotation.Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    /**
     * Findet alle Lohnabrechnungen eines Mitarbeiters.
     */
    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    public ResponseEntity<List<LohnabrechnungDto>> getByMitarbeiter(@PathVariable Long mitarbeiterId) {
        return ResponseEntity.ok(lohnabrechnungService.findByMitarbeiterId(mitarbeiterId));
    }

    /**
     * Findet alle Lohnabrechnungen eines Jahres.
     */
    @GetMapping("/jahr/{jahr}")
    public ResponseEntity<List<LohnabrechnungDto>> getByJahr(@PathVariable Integer jahr) {
        return ResponseEntity.ok(lohnabrechnungService.findByJahr(jahr));
    }

    /**
     * Findet alle Lohnabrechnungen eines Steuerberaters in einem Jahr.
     */
    @GetMapping("/steuerberater/{steuerberaterId}/jahr/{jahr}")
    public ResponseEntity<List<LohnabrechnungDto>> getBySteuerberaterAndJahr(
            @PathVariable Long steuerberaterId, @PathVariable Integer jahr) {
        return ResponseEntity.ok(lohnabrechnungService.findBySteuerberaterAndJahr(steuerberaterId, jahr));
    }

    /**
     * Findet alle verfügbaren Jahre.
     */
    @GetMapping("/jahre")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        return ResponseEntity.ok(lohnabrechnungService.findAvailableYears());
    }

    /**
     * Findet eine Lohnabrechnung nach ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LohnabrechnungDto> getById(@PathVariable Long id) {
        LohnabrechnungDto dto = lohnabrechnungService.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Lädt die PDF einer Lohnabrechnung herunter.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) {
        try {
            LohnabrechnungDto dto = lohnabrechnungService.findById(id);
            if (dto == null) {
                return ResponseEntity.notFound().build();
            }

            // Datei laden (Lohnabrechnung PDFs sind im mail-attachment-dir)
            Path filePath = Path.of(mailAttachmentDir).resolve(dto.getOriginalDateiname()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String filename = dto.getOriginalDateiname() != null 
                    ? dto.getOriginalDateiname() 
                    : "lohnabrechnung.pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Löscht eine Lohnabrechnung.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        lohnabrechnungService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
