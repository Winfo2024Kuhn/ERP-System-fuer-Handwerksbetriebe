package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.service.BestellungPdfService;
import org.example.kalkulationsprogramm.service.BestellungService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
