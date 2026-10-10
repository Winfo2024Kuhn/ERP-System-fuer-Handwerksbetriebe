package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.service.ZeiterfassungApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Jahressaldo eines Mitarbeiters für den Zeiterfassungs-Kalender im Büro. Läuft über die
 * Büro-Anmeldung und die Mitarbeiter-ID – das Büro braucht dafür keinen fremden Anmelde-Code.
 */
@RestController
@RequiredArgsConstructor
public class MitarbeiterSaldoController {

    private final ZeiterfassungApiService zeiterfassungApiService;

    @GetMapping("/api/zeitverwaltung/mitarbeiter/{mitarbeiterId}/saldo")
    public ResponseEntity<Map<String, Object>> saldo(@PathVariable Long mitarbeiterId,
            @RequestParam(required = false) Integer jahr) {
        if (mitarbeiterId == null || mitarbeiterId <= 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(zeiterfassungApiService.getSaldoFuerMitarbeiter(mitarbeiterId, jahr));
    }
}
