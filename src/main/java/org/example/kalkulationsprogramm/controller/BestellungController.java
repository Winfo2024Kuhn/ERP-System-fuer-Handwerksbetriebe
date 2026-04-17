package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ConfirmRequestDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ConfirmResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.PreviewResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.ManuelleBestellpositionDto;
import org.example.kalkulationsprogramm.service.BestellungPdfService;
import org.example.kalkulationsprogramm.service.BestellungService;
import org.example.kalkulationsprogramm.service.HicadImportService;
import org.springframework.web.multipart.MultipartFile;
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
    private final HicadImportService hicadImportService;

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

    @PostMapping(value = "/import/hicad/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PreviewResponseDto> hicadPreview(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(hicadImportService.preview(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        } catch (java.io.IOException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Reason", "Datei konnte nicht gelesen werden")
                    .build();
        }
    }

    @PostMapping(value = "/import/hicad/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfirmResponseDto> hicadConfirm(@RequestBody ConfirmRequestDto req) {
        try {
            return ResponseEntity.ok(hicadImportService.confirm(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }
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
