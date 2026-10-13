package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.UrlaubUebertragService;
import org.example.kalkulationsprogramm.service.UrlaubUebertragService.Stand;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Resturlaub aus dem Vorjahr ansehen und von Hand festlegen (PC, Mitarbeiter-Bereich).
 *
 * <p>Liegt unter {@code /api/urlaub/**}: nur wer die Zeiterfassung sehen darf
 * ({@code ZeiterfassungZugriffInterceptor}). Die Handy-App erreicht den Endpunkt
 * nicht, er steht bewusst nicht in {@code MobileApiPolicy}.
 */
@RestController
@RequiredArgsConstructor
public class UrlaubUebertragController {

    private final UrlaubUebertragService service;

    @GetMapping("/api/urlaub/uebertrag")
    public Stand laden(@RequestParam Long mitarbeiterId, @RequestParam Integer jahr) {
        return service.laden(mitarbeiterId, jahr);
    }

    @PutMapping("/api/urlaub/uebertrag")
    public Stand speichern(@RequestBody Eingabe eingabe) {
        return service.speichern(eingabe.mitarbeiterId(), eingabe.jahr(), eingabe.tage());
    }

    /** tage: {@code null} = wieder automatisch rechnen. */
    public record Eingabe(Long mitarbeiterId, Integer jahr, BigDecimal tage) {}
}
