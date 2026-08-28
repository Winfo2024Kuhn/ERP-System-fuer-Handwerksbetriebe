package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotResponseDto;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.VerlaufPunktDto;
import org.example.kalkulationsprogramm.service.WebsiteAnalyticsSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Lese-Endpoint fuer das ERP-Frontend (Erfolgsanalyse-Seite).
 * Liefert den juengsten gespeicherten Website-Snapshot. Liegt noch keiner
 * vor, antwortet der Endpoint mit 204.
 */
@RestController
@RequestMapping("/api/website-analytics")
@RequiredArgsConstructor
public class WebsiteAnalyticsSnapshotController {

    private final WebsiteAnalyticsSnapshotService service;

    @GetMapping("/latest")
    public ResponseEntity<AnalyticsSnapshotResponseDto> latest() {
        Optional<AnalyticsSnapshotResponseDto> latest = service.findLatest();
        return latest.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Besucherverlauf fuer die Linie im Insights-Tab. Die Begrenzung des
     * Zeitraums macht der Service, nicht der Controller.
     */
    @GetMapping("/verlauf")
    public ResponseEntity<List<VerlaufPunktDto>> verlauf(
            @RequestParam(defaultValue = "30") int tage) {
        return ResponseEntity.ok(service.findVerlauf(tage));
    }
}
