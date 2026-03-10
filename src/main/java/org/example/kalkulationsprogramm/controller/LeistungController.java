package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.service.LeistungService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
