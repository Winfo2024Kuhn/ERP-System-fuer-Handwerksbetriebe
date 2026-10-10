package org.example.kalkulationsprogramm.controller;

import java.time.Instant;

import org.example.kalkulationsprogramm.service.TailscaleFunnelFreigabe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Einstellung „Zeiterfassung von unterwegs": Tailscale-App auf den Handys oder
 * Freigabe über Tailscale Funnel. Liegt unter {@code /api/settings}, also nur für Admins.
 */
@RestController
@RequestMapping("/api/settings/zeiterfassung-zugang")
@RequiredArgsConstructor
public class ZeiterfassungZugangController {

    private final TailscaleFunnelFreigabe funnelFreigabe;

    @GetMapping
    public ResponseEntity<ZugangResponse> lesen() {
        return ResponseEntity.ok(stand());
    }

    @PutMapping
    public ResponseEntity<ZugangResponse> speichern(@RequestBody ZugangRequest req) {
        if (req == null || req.funnelFreigegeben() == null) {
            return ResponseEntity.badRequest().build();
        }
        funnelFreigabe.setzen(req.funnelFreigegeben());
        return ResponseEntity.ok(stand());
    }

    private ZugangResponse stand() {
        return new ZugangResponse(funnelFreigabe.istFreigegeben(), funnelFreigabe.letzteAnfrage(),
                funnelFreigabe.letzteAbgewiesen());
    }

    /** {@code letzteFunnelAnfrage}: seit dem Start der App, sonst {@code null}. */
    record ZugangResponse(boolean funnelFreigegeben, Instant letzteFunnelAnfrage, boolean letzteFunnelAnfrageAbgewiesen) {}

    record ZugangRequest(Boolean funnelFreigegeben) {}
}
