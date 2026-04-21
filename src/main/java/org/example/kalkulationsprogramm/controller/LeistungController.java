package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.dto.Leistung.WpsRefDto;
import org.example.kalkulationsprogramm.service.LeistungService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/leistungen")
@RequiredArgsConstructor
public class LeistungController {

    private final LeistungService leistungService;

    @GetMapping
    public List<LeistungDto> getAll() {
        return leistungService.getAllLeistungen();
    }

    @PostMapping
    public ResponseEntity<LeistungDto> create(@RequestBody LeistungCreateDto dto) {
        return ResponseEntity.ok(leistungService.createLeistung(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeistungDto> update(@PathVariable Long id, @RequestBody LeistungCreateDto dto) {
        return ResponseEntity.ok(leistungService.updateLeistung(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        leistungService.deleteLeistung(id);
        return ResponseEntity.noContent().build();
    }

    /** Die verknüpften Schweißanweisungen einer Leistung. */
    @GetMapping("/{id}/wps")
    public ResponseEntity<List<WpsRefDto>> getVerknuepfteWps(@PathVariable Long id) {
        return ResponseEntity.ok(leistungService.getVerknuepfteWps(id));
    }

    /** Setzt die WPS-Menge einer Leistung neu (Replace-Semantik). */
    @PutMapping("/{id}/wps")
    public ResponseEntity<List<WpsRefDto>> setVerknuepfteWps(
            @PathVariable Long id,
            @RequestBody WpsIdsRequest body) {
        Set<Long> ids = body != null && body.wpsIds() != null ? Set.copyOf(body.wpsIds()) : Set.of();
        return ResponseEntity.ok(leistungService.setVerknuepfteWps(id, ids));
    }

    public record WpsIdsRequest(List<Long> wpsIds) {}
}
