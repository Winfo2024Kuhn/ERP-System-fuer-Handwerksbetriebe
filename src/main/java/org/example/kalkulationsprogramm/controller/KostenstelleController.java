package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.service.KostenstelleBerechtigungService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Kostenstellen der Firma. Ansehen darf jeder Angemeldete; anlegen, ändern, löschen und wiederherstellen nur, wer das Recht
 * „Kostenstellen verwalten“ hat (Administratoren immer). {@code /berechtigung} darf jeder fragen, damit die Oberfläche die Knöpfe
 * zeigt oder nicht. Löschen blendet nur aus, bisherige Buchungen bleiben der Kostenstelle zugeordnet.
 */
@RestController
@RequestMapping("/api/firma/kostenstellen")
@RequiredArgsConstructor
public class KostenstelleController {

    private final KostenstelleService kostenstelleService;
    private final KostenstelleBerechtigungService berechtigung;

    /** Darf der angemeldete Benutzer Kostenstellen verwalten? */
    public record Berechtigung(boolean darfAendern) { }

    @GetMapping("/berechtigung")
    public Berechtigung berechtigung(Authentication authentication) {
        return new Berechtigung(berechtigung.darfVerwalten(authentication));
    }

    @GetMapping
    public List<KostenstelleDto> getKostenstellen() {
        return kostenstelleService.findAll();
    }

    @GetMapping("/geloescht")
    public List<KostenstelleDto> getGeloeschte(Authentication authentication) {
        berechtigung.verlange(authentication);
        return kostenstelleService.findGeloeschte();
    }

    @GetMapping("/typ/{typ}")
    public List<KostenstelleDto> getKostenstellenByTyp(@PathVariable KostenstellenTyp typ) {
        return kostenstelleService.findByTyp(typ);
    }

    @GetMapping("/{id}")
    public ResponseEntity<KostenstelleDto> getKostenstelle(@PathVariable Long id) {
        KostenstelleDto dto = kostenstelleService.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public KostenstelleDto erstelleKostenstelle(@RequestBody KostenstelleDto dto, Authentication authentication) {
        berechtigung.verlange(authentication);
        dto.setId(null); // Neue Kostenstelle
        return kostenstelleService.speichern(dto);
    }

    @PutMapping("/{id}")
    public KostenstelleDto aktualisiereKostenstelle(@PathVariable Long id, @RequestBody KostenstelleDto dto,
            Authentication authentication) {
        berechtigung.verlange(authentication);
        dto.setId(id);
        return kostenstelleService.speichern(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loescheKostenstelle(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        kostenstelleService.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/wiederherstellen")
    public KostenstelleDto wiederherstellen(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        return kostenstelleService.wiederherstellen(id);
    }

    @PostMapping("/init")
    public List<KostenstelleDto> initKostenstellen(Authentication authentication) {
        berechtigung.verlange(authentication);
        kostenstelleService.erstelleStandardKostenstellen();
        return kostenstelleService.findAll();
    }
}
