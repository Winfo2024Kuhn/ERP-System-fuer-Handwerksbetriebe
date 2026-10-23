package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.dto.Abteilung.AbteilungNameDto;
import org.example.kalkulationsprogramm.dto.Abteilung.AbteilungResponseDto;
import org.example.kalkulationsprogramm.service.AbteilungService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Abteilungen lesen (alle Angemeldeten, z. B. für den Mitarbeiter-Editor) sowie
 * anlegen, umbenennen und löschen (nur Admins, siehe {@code SecurityConfig}).
 * Die Rechte je Abteilung liegen im {@link AbteilungBerechtigungController}.
 */
@RestController
@RequestMapping("/api/abteilungen")
@RequiredArgsConstructor
public class AbteilungController {

    private final AbteilungService abteilungService;

    @GetMapping
    public List<AbteilungResponseDto> getAlleAbteilungen() {
        return abteilungService.findeAlle().stream().map(AbteilungController::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<AbteilungResponseDto> erstelleAbteilung(@RequestBody AbteilungNameDto body) {
        Abteilung abteilung = abteilungService.erstelle(body.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(abteilung));
    }

    @PutMapping("/{id}")
    public AbteilungResponseDto benenneAbteilungUm(@PathVariable Long id, @RequestBody AbteilungNameDto body) {
        return toDto(abteilungService.benenneUm(id, body.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loescheAbteilung(@PathVariable Long id) {
        abteilungService.loesche(id);
        return ResponseEntity.noContent().build();
    }

    private static AbteilungResponseDto toDto(Abteilung abteilung) {
        AbteilungResponseDto dto = new AbteilungResponseDto();
        dto.setId(abteilung.getId());
        dto.setName(abteilung.getName());
        return dto;
    }
}
