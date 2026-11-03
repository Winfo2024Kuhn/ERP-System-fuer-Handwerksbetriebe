package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerRechteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Rechte je Abteilung an einem obersten Ordner der Firmendokumente. Nur Admins (SecurityConfig,
 * {@code /api/firmendokumente/ordner/*}{@code /rechte}); Prüfung der Eingaben im Dienst.
 */
@RestController
@RequestMapping("/api/firmendokumente/ordner/{id}/rechte")
@RequiredArgsConstructor
public class FirmendokumentOrdnerRechteController {

    private final FirmendokumentOrdnerRechteService service;

    @GetMapping
    public FirmendokumentOrdnerRechteDto laden(@PathVariable Long id) {
        return service.laden(id);
    }

    @PutMapping
    public FirmendokumentOrdnerRechteDto speichern(@PathVariable Long id,
            @RequestBody FirmendokumentOrdnerRechteSpeichernDto eingabe) {
        return service.speichern(id, eingabe);
    }
}
