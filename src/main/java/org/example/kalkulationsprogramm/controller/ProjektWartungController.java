package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.PreisNachtragErgebnis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

/**
 * Wartungsaktionen rund um Projekte, die den gesamten Bestand anfassen.
 *
 * <p>Bewusst unter {@code /api/admin/...} statt unter {@code /api/projekte/...}:
 * Der Pfad {@code /api/projekte/**} ist für die Zeiterfassungs-PWA freigeschaltet
 * und kommt dort ohne Anmeldung durch. Unter {@code /api/admin} greift dagegen die
 * normale API-Kette mit Session-Login und Admin-Rolle.</p>
 */
@RestController
@RequestMapping("/api/admin/projekte")
@AllArgsConstructor
public class ProjektWartungController {

    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    /**
     * Trägt bei allen Projekten ohne Auftragspreis den Preis aus den Dokumenten nach
     * (Angebot/Auftragsbestätigung/Nachtragsangebot, sonst Summe der Rechnungen).
     * Projekte, die bereits einen Preis haben, bleiben unverändert.
     */
    @PostMapping("/preise-nachtragen")
    public ResponseEntity<PreisNachtragErgebnis> tragePreiseNach() {
        return ResponseEntity.ok(ausgangsGeschaeftsDokumentService.trageFehlendePreiseNach());
    }
}
