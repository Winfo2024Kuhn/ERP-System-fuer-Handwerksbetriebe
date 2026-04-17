package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.ManuelleBestellpositionDto;
import org.example.kalkulationsprogramm.service.BestellungPdfService;
import org.example.kalkulationsprogramm.service.BestellungService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/bestellungen")
@AllArgsConstructor
public class BestellungController {

    private final BestellungService bestellungService;
    private final BestellungPdfService bestellungPdfService;

    @GetMapping("/offen")
    public List<BestellungResponseDto> offeneBestellungen() {
        return bestellungService.findeOffeneBestellungen();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> setBestellt(@PathVariable Long id, @RequestParam boolean bestellt) {
        bestellungService.setBestellt(id, bestellt);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projekt/{projektId}/pdf")
    public ResponseEntity<Resource> pdfForProjekt(@PathVariable Long projektId) throws java.io.IOException {
        java.nio.file.Path pdf = bestellungPdfService.generatePdfForProjekt(projektId);
        Resource res = new InputStreamResource(java.nio.file.Files.newInputStream(pdf));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=bestellung.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(res);
    }

    @GetMapping("/lieferant/{lieferantId}/pdf")
    public ResponseEntity<Resource> pdfForLieferant(@PathVariable Long lieferantId) throws java.io.IOException {
        java.nio.file.Path pdf = bestellungPdfService.generatePdfForLieferant(lieferantId);
        Resource res = new InputStreamResource(java.nio.file.Files.newInputStream(pdf));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=bestellung-lieferant.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(res);
    }

    @PostMapping("/manuell")
    public ResponseEntity<BestellungResponseDto> manuellePosition(@RequestBody ManuelleBestellpositionDto dto) {
        return ResponseEntity.ok(bestellungService.manuellePosition(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BestellungResponseDto> aktualisierePosition(
            @PathVariable Long id,
            @RequestBody ManuelleBestellpositionDto dto) {
        try {
            return ResponseEntity.ok(bestellungService.aktualisierePosition(id, dto));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
    }

    @PostMapping("/lieferant/{lieferantId}/markiere-exportiert")
    public ResponseEntity<Map<String, Integer>> markiereLieferantAlsExportiert(@PathVariable Long lieferantId) {
        int count = bestellungService.markiereLieferantAlsExportiert(lieferantId);
        return ResponseEntity.ok(Map.of("markiert", count));
    }

    @DeleteMapping("/{id}/freitext")
    public ResponseEntity<Void> loescheFreiePosition(@PathVariable Long id) {
        bestellungService.loeschePosition(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zeugnis-default")
    public ResponseEntity<Map<String, String>> zeugnisDefault(
            @RequestParam Integer kategorieId,
            @RequestParam(required = false) String excKlasse) {
        return bestellungService.zeugnisDefault(kategorieId, excKlasse)
                .map(z -> ResponseEntity.ok(Map.of("zeugnisTyp", z.name())))
                .orElse(ResponseEntity.ok(Map.of()));
    }
}
