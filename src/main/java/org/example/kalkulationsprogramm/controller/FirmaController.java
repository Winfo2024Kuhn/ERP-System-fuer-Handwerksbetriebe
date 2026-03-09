package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller für Firmenstammdaten, Kostenstellen und Steuerberater.
 */
@RestController
@RequestMapping("/api/firma")
@RequiredArgsConstructor
public class FirmaController {

    private final FirmeninformationService firmeninformationService;
    private final KostenstelleService kostenstelleService;
    private final SteuerberaterKontaktService steuerberaterKontaktService;

    // ==================== FIRMENINFORMATION ====================

    @GetMapping
    public ResponseEntity<FirmeninformationDto> getFirmeninformation() {
        return ResponseEntity.ok(firmeninformationService.getFirmeninformation());
    }

    @PutMapping
    public ResponseEntity<FirmeninformationDto> speichernFirmeninformation(@RequestBody FirmeninformationDto dto) {
        return ResponseEntity.ok(firmeninformationService.speichern(dto));
    }

    // ==================== KOSTENSTELLEN ====================

    @GetMapping("/kostenstellen")
    public ResponseEntity<List<KostenstelleDto>> getKostenstellen() {
        return ResponseEntity.ok(kostenstelleService.findAll());
    }

    @GetMapping("/kostenstellen/typ/{typ}")
    public ResponseEntity<List<KostenstelleDto>> getKostenstellenByTyp(@PathVariable KostenstellenTyp typ) {
        return ResponseEntity.ok(kostenstelleService.findByTyp(typ));
    }

    @GetMapping("/kostenstellen/{id}")
    public ResponseEntity<KostenstelleDto> getKostenstelle(@PathVariable Long id) {
        KostenstelleDto dto = kostenstelleService.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/kostenstellen")
    public ResponseEntity<KostenstelleDto> erstelleKostenstelle(@RequestBody KostenstelleDto dto) {
        dto.setId(null); // Neue Kostenstelle
        return ResponseEntity.ok(kostenstelleService.speichern(dto));
    }

    @PutMapping("/kostenstellen/{id}")
    public ResponseEntity<KostenstelleDto> aktualisiereKostenstelle(
            @PathVariable Long id, 
            @RequestBody KostenstelleDto dto) {
        dto.setId(id);
        return ResponseEntity.ok(kostenstelleService.speichern(dto));
    }

    @DeleteMapping("/kostenstellen/{id}")
    public ResponseEntity<Void> loescheKostenstelle(@PathVariable Long id) {
        kostenstelleService.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/kostenstellen/init")
    public ResponseEntity<List<KostenstelleDto>> initKostenstellen() {
        kostenstelleService.erstelleStandardKostenstellen();
        return ResponseEntity.ok(kostenstelleService.findAll());
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
