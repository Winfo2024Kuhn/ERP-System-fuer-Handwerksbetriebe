package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Ids.IdsAktivDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangSpeichernDto;
import org.example.kalkulationsprogramm.service.ids.IdsEinstellungenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Einrichtung der Shop-Anbindung (IDS): nur Admins ({@code SecurityConfig}). */
@RestController
@RequestMapping("/api/ids/einstellungen")
@RequiredArgsConstructor
public class IdsEinstellungenController {

    private final IdsEinstellungenService service;

    @GetMapping
    public IdsEinstellungenDto lade() {
        return service.lade();
    }

    @PutMapping
    public IdsEinstellungenDto setzeAktiv(@RequestBody IdsAktivDto dto) {
        return service.setzeAktiv(dto != null && dto.aktiv());
    }

    @PutMapping("/zugaenge/{lieferantId}")
    public IdsZugangDto speichereZugang(@PathVariable Long lieferantId, @RequestBody IdsZugangSpeichernDto dto) {
        return service.speichereZugang(lieferantId, dto);
    }

    @DeleteMapping("/zugaenge/{lieferantId}")
    public ResponseEntity<Void> loescheZugang(@PathVariable Long lieferantId) {
        service.loescheZugang(lieferantId);
        return ResponseEntity.noContent().build();
    }
}
