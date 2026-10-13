package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Darf der angemeldete PC-Benutzer den Menü-Bereich „Zeiterfassung“ sehen?
 * Die Oberfläche blendet danach Menü und Seiten ein oder aus.
 */
@RestController
@RequiredArgsConstructor
public class ZeiterfassungSichtbarkeitController {

    public record Sichtbarkeit(boolean darfZeiterfassungSehen) {
    }

    private final ZeiterfassungSichtbarkeitService sichtbarkeitService;

    @GetMapping("/api/zeitverwaltung/sichtbarkeit")
    public Sichtbarkeit sichtbarkeit(Authentication authentication) {
        return new Sichtbarkeit(sichtbarkeitService.darfZeiterfassungSehen(authentication));
    }
}
