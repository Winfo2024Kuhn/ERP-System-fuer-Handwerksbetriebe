package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.WerkstoffzeugnisNachleseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

/**
 * Einmalige Wartungsläufe für Lieferanten-Dokumente.
 *
 * <p>Unter {@code /api/admin/...}: dort greift die normale API-Kette mit
 * Session-Login, Admin-Rolle und CSRF-Schutz.</p>
 */
@RestController
@RequestMapping("/api/admin/lieferant-dokumente")
@AllArgsConstructor
public class LieferantDokumentWartungController {

    private final WerkstoffzeugnisNachleseService nachleseService;

    /**
     * Lässt alle Werkstoffzeugnisse ohne Nummer oder ohne Positionen nacheinander von
     * der KI lesen und in ihre Kette hängen. Kostet KI-Aufrufe und dauert je Zeugnis
     * einige Sekunden – der Lauf antwortet erst, wenn alle durch sind. Läuft schon
     * einer, kommt 409.
     */
    @PostMapping("/werkstoffzeugnisse/nachlesen")
    public ResponseEntity<?> liesWerkstoffzeugnisseNach(Authentication authentication) {
        // Fürs Protokoll nur die Benutzer-ID, keinen Namen (DSGVO)
        Long adminId = authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal p
                ? p.getId()
                : null;
        try {
            return ResponseEntity.ok(nachleseService.liesZeugnisseNach(adminId));
        } catch (WerkstoffzeugnisNachleseService.LaufAktivException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }
}
