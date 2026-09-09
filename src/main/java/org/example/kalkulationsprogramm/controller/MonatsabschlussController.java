package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.MonatsabschlussDto;
import org.example.kalkulationsprogramm.service.MonatsSaldoService;
import org.example.kalkulationsprogramm.service.MonatsabschlussBerechtigungService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zeitverwaltung/monatsabschluesse")
@RequiredArgsConstructor
public class MonatsabschlussController {
    private final MonatsSaldoService saldoService;
    private final MonatsabschlussBerechtigungService berechtigungService;

    @GetMapping("/berechtigung")
    public MonatsabschlussDto.Berechtigung berechtigung(Authentication authentication) {
        return new MonatsabschlussDto.Berechtigung(berechtigungService.darfMonatAbschliessen(authentication));
    }

    @GetMapping("/{mitarbeiterId}/{jahr}/{monat}")
    public MonatsabschlussDto status(@PathVariable Long mitarbeiterId, @PathVariable int jahr, @PathVariable int monat) {
        return saldoService.status(mitarbeiterId, jahr, monat);
    }

    @PostMapping("/{mitarbeiterId}/{jahr}/{monat}/abschliessen")
    public MonatsabschlussDto abschliessen(@PathVariable Long mitarbeiterId, @PathVariable int jahr,
            @PathVariable int monat, Authentication authentication) {
        return saldoService.abschliessen(mitarbeiterId, jahr, monat, authentication);
    }

    @PostMapping("/{mitarbeiterId}/{jahr}/{monat}/oeffnen")
    public MonatsabschlussDto oeffnen(@PathVariable Long mitarbeiterId, @PathVariable int jahr,
            @PathVariable int monat, Authentication authentication) {
        return saldoService.oeffnen(mitarbeiterId, jahr, monat, authentication);
    }
}
