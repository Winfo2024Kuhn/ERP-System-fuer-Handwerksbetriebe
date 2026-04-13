package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.En1090ReportService;
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

    @GetMapping("/wpk/{projektId}")
    public En1090ReportService.WpkStatus getWpkStatus(@PathVariable Long projektId) {
        return en1090ReportService.getWpkStatus(projektId);
    }
}
