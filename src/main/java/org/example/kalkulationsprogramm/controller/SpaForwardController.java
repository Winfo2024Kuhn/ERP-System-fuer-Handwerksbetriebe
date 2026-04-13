package org.example.kalkulationsprogramm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Leitet alle Frontend-Routen (React Router) an index.html weiter, damit
 * Browser-Refreshs und direkte URL-Aufrufe keine leere Seite liefern.
 *
 * Greift nur für Pfade OHNE Datei-Extension (kein Punkt im letzten Segment),
 * damit statische Assets (.js, .css, .png …) unverändert ausgeliefert werden.
 * API-Endpunkte (/api/**) sind durch spezifischere @RestController-Mappings
 * vorrangig und werden nicht durch diesen Controller bedient.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/{path:[^\\.]*}",
            "/**/{path:[^\\.]*}"
    })
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
