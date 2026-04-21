package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ArtikelDurchschnittspreisService;
import org.example.kalkulationsprogramm.service.ArtikelDurchschnittspreisService.BackfillErgebnis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Admin-Endpoints rund um Artikel-Stammdaten (Batch-Jobs, Backfills).
 * Siehe {@code docs/ADMIN_ENDPOINTS.md}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/artikel")
public class AdminArtikelController {

    private final ArtikelDurchschnittspreisService durchschnittspreisService;

    @PostMapping("/durchschnittspreis/backfill")
    public ResponseEntity<BackfillErgebnis> backfillDurchschnittspreis() {
        return ResponseEntity.ok(durchschnittspreisService.backfillAlle());
    }
}
