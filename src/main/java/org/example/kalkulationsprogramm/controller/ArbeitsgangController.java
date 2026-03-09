package org.example.kalkulationsprogramm.controller;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Abteilung.AbteilungResponseDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangErstellenDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangStundensatzDto;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.service.ArbeitsgangManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ArbeitsgangController {
    private final ArbeitsgangManagementService arbeitsgangManagementService;
    private final ArbeitsgangMapper arbeitsgangMapper;
    private final AbteilungRepository abteilungRepository;

    // ==================== Abteilung Endpoints ====================

    @GetMapping("/abteilungen")
    public ResponseEntity<List<AbteilungResponseDto>> getAlleAbteilungen() {
        List<AbteilungResponseDto> dtos = abteilungRepository.findAll().stream()
                .map(this::toAbteilungDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/abteilungen")
    public ResponseEntity<AbteilungResponseDto> erstelleAbteilung(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Abteilung abteilung = new Abteilung();
        abteilung.setName(name.trim());
        Abteilung saved = abteilungRepository.save(abteilung);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAbteilungDto(saved));
    }

    @DeleteMapping("/abteilungen/{id}")
    public ResponseEntity<Void> loescheAbteilung(@PathVariable Long id) {
        try {
            Abteilung abteilung = abteilungRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Abteilung nicht gefunden"));
            if (!abteilung.getArbeitsgaenge().isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            abteilungRepository.delete(abteilung);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private AbteilungResponseDto toAbteilungDto(Abteilung abteilung) {
        AbteilungResponseDto dto = new AbteilungResponseDto();
        dto.setId(abteilung.getId());
        dto.setName(abteilung.getName());
        return dto;
    }

    // ==================== Arbeitsgang Endpoints ====================

    @PostMapping("/arbeitsgaenge")
    public ResponseEntity<ArbeitsgangResponseDto> erstelleNeuenArbeitsgang(
            @RequestBody ArbeitsgangErstellenDto arbeitsgangDto) {
        Arbeitsgang gespeicherterArbeitsgang = this.arbeitsgangManagementService.erstelleArbeitsgang(arbeitsgangDto);
        ArbeitsgangResponseDto responseDto = this.arbeitsgangMapper.toArbeitsgangResponseDto(gespeicherterArbeitsgang);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @DeleteMapping("/arbeitsgaenge/{arbeitsgangID}")
    public ResponseEntity<Void> loescheArbeitsgang(@PathVariable Long arbeitsgangID) {
        try {
            this.arbeitsgangManagementService.loescheArbeitsgang(arbeitsgangID);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/arbeitsgaenge")
    public ResponseEntity<List<ArbeitsgangResponseDto>> getAlleArbeitsgaenge() {
        List<Arbeitsgang> arbeitsgaenge = arbeitsgangManagementService.findeAlle();
        List<ArbeitsgangResponseDto> responseDtos = arbeitsgaenge.stream()
                .map(arbeitsgangMapper::toArbeitsgangResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @PostMapping("/arbeitsgaenge/stundensaetze")
    public ResponseEntity<Void> aktualisiereStundensaetze(@RequestBody List<ArbeitsgangStundensatzDto> dtos) {
        arbeitsgangManagementService.aktualisiereStundensaetze(dtos);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/arbeitsgaenge/{id}/stundensatz")
    public ResponseEntity<ArbeitsgangResponseDto> aktualisiereEinzelnenStundensatz(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            BigDecimal neuerSatz = new BigDecimal(body.get("stundensatz").toString());
            arbeitsgangManagementService.aktualisiereEinzelnenStundensatz(id, neuerSatz);

            Arbeitsgang arbeitsgang = arbeitsgangManagementService.findeAlle().stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Arbeitsgang nicht gefunden"));

            return ResponseEntity.ok(arbeitsgangMapper.toArbeitsgangResponseDto(arbeitsgang));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
