package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.En1090Anforderungen;
import org.example.kalkulationsprogramm.service.En1090AnforderungenService;
import org.example.kalkulationsprogramm.service.En1090ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-API für EN 1090 WPK-Statusberichte und Projekt-Compliance-Übersicht.
 */
@RestController
@RequestMapping("/api/en1090")
@RequiredArgsConstructor
public class En1090Controller {

    private final En1090ReportService en1090ReportService;
    private final En1090AnforderungenService en1090AnforderungenService;

    @GetMapping("/wpk/{projektId}")
    public En1090ReportService.WpkStatus getWpkStatus(@PathVariable Long projektId) {
        return en1090ReportService.getWpkStatus(projektId);
    }

    /**
     * Zentrale Abfrage für alle EN-1090-Folgemodule: Fällt ein Projekt unter
     * den vollen EN-1090-2-Ablauf? Antwort ist bewusst minimal gehalten und
     * bei Bedarf in späteren Milestones erweiterbar.
     */
    @GetMapping("/anforderungen/{projektId}")
    public ResponseEntity<En1090Anforderungen> getAnforderungen(@PathVariable Long projektId) {
        try {
            return ResponseEntity.ok(en1090AnforderungenService.fuerProjekt(projektId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
