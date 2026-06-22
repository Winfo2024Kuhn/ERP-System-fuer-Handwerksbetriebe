package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/artikel/kategorien")
@AllArgsConstructor
public class ArtikelKategorieController {

    private final KategorieService kategorieService;

    @GetMapping("/haupt")
    public List<KategorieResponseDto> hauptKategorien() {
        return kategorieService.findeHauptkategorien();
    }

    @GetMapping("/alle")
    public List<KategorieResponseDto> alleKategorien() {
        return kategorieService.alleKategorien();
    }

    @GetMapping("/{parentId}/unterkategorien")
    public List<KategorieResponseDto> unterKategorien(@PathVariable Integer parentId) {
        return kategorieService.findeUnterkategorien(parentId);
    }

    @PostMapping
    public ResponseEntity<KategorieResponseDto> erstelleKategorie(@RequestBody KategorieCreateDto dto) {
        try {
            KategorieResponseDto created = kategorieService.erstelleKategorie(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Setzt die typischen Liefer-Rollen einer Kategorie (ersetzt die bisherige Zuordnung).
     */
    @PutMapping("/{id}/rollen")
    public ResponseEntity<KategorieResponseDto> aktualisiereRollen(@PathVariable Integer id,
            @RequestBody Set<LieferantRolle> rollen) {
        return ResponseEntity.ok(kategorieService.aktualisiereTypischeRollen(id, rollen));
    }

    /**
     * Liefert die effektiven Liefer-Rollen einer Kategorie (eigene Rollen, sonst von der
     * Oberkategorie geerbt). Steuert den Lieferanten-Vorschlag beim Preis-Eintragen am Artikel.
     */
    @GetMapping("/{id}/effektive-rollen")
    public ResponseEntity<Set<LieferantRolle>> effektiveRollen(@PathVariable Integer id) {
        return ResponseEntity.ok(kategorieService.findeEffektiveRollen(id));
    }
}
