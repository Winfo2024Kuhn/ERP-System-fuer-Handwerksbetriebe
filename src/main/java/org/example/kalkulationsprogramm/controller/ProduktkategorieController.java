package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieAnalyseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieErstellenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.service.ProduktkategorieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/produktkategorien")
@AllArgsConstructor
public class ProduktkategorieController {

    private final ProduktkategorieService produktkategorieService;

    // ... GET-Methoden bleiben unverändert ...
    @GetMapping
    public ResponseEntity<List<ProduktkategorieResponseDto>> getAlleKategorien(
            @RequestParam(value = "light", required = false, defaultValue = "false") boolean light) {
        return ResponseEntity.ok(produktkategorieService.findeAlleKategorien(light));
    }

    @GetMapping("/haupt")
    public ResponseEntity<List<ProduktkategorieResponseDto>> getHauptkategorien(
            @RequestParam(value = "light", required = false, defaultValue = "false") boolean light) {
        return ResponseEntity.ok(produktkategorieService.findeHauptkategorien(light));
    }

    // NEU: Endpunkt, um eine einzelne Kategorie anhand ihrer ID abzurufen
    @GetMapping("/{id}")
    public ResponseEntity<ProduktkategorieResponseDto> getKategorieById(@PathVariable Long id) {
        try {
            ProduktkategorieResponseDto dto = produktkategorieService.findeKategorieById(id);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{kategorieId}/analyse")
    public ResponseEntity<ProduktkategorieAnalyseDto> getAnalyse(
            @PathVariable Long kategorieId,
            @RequestParam(value = "jahr", required = false) Integer jahr) {
        return ResponseEntity.ok(produktkategorieService.analysiereKategorie(kategorieId, jahr));
    }

    @GetMapping("/{parentId}/unterkategorien")
    public ResponseEntity<List<ProduktkategorieResponseDto>> getUnterkategorien(
            @PathVariable Long parentId,
            @RequestParam(value = "light", required = false, defaultValue = "false") boolean light) {
        return ResponseEntity.ok(produktkategorieService.findeUnterkategorie(parentId, light));
    }

    @GetMapping("/suche")
    public ResponseEntity<List<ProduktkategorieResponseDto>> sucheLeafKategorien(
            @RequestParam("q") String suchbegriff) {
        return ResponseEntity.ok(produktkategorieService.sucheLeafKategorien(suchbegriff));
    }

    // Kategorie-Daten werden als normale Form-Fields übertragen, Bild optional als
    // Datei
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ProduktkategorieResponseDto> erstelleKategorie(
            @ModelAttribute ProduktkategorieErstellenDto kategorieDto,
            @RequestParam(value = "bild", required = false) MultipartFile bild) {

        ProduktkategorieResponseDto neueKategorie = produktkategorieService.erstelleKategorie(kategorieDto, bild);
        return new ResponseEntity<>(neueKategorie, HttpStatus.CREATED);
    }

    @PatchMapping("/{kategorieId}/beschreibung")
    public ResponseEntity<ProduktkategorieResponseDto> aktualisiereBeschreibung(@PathVariable Long kategorieId,
            @RequestBody java.util.Map<String, String> body) {
        try {
            String beschreibung = body.getOrDefault("beschreibung", "");
            ProduktkategorieResponseDto dto = produktkategorieService.aktualisiereBeschreibung(kategorieId,
                    beschreibung);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping(value = "/{id}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ProduktkategorieResponseDto> aktualisiereKategorie(
            @PathVariable Long id,
            @ModelAttribute ProduktkategorieErstellenDto kategorieDto,
            @RequestParam(value = "bild", required = false) MultipartFile bild) {

        try {
            ProduktkategorieResponseDto updated = produktkategorieService.aktualisiereKategorie(id, kategorieDto, bild);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{kategorieId}")
    public ResponseEntity<Void> loescheKategorie(@PathVariable Long kategorieId) {
        try {
            produktkategorieService.loescheKategorie(kategorieId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
