package org.example.kalkulationsprogramm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA Fallback Controller für die Mobile Zeiterfassungs-PWA.
 * 
 * WICHTIG: Kein @RequestMapping("/zeiterfassung") verwenden, da sonst
 * statische Assets unter /zeiterfassung/assets/* auch abgefangen werden!
 * 
 * Die PWA wird unter /zeiterfassung/ ausgeliefert und verwendet React Router
 * mit basename="/zeiterfassung". Nur explizite App-Routen werden hier gemappt.
 * Statische Assets (.js, .css, .png etc.) werden direkt von Spring Boot
 * aus /static/zeiterfassung/ ausgeliefert.
 */
@Controller
public class ZeiterfassungController {

    private static final String FORWARD_TO_INDEX = "forward:/zeiterfassung/index.html";

    // ==================== Root ====================

    @GetMapping("/zeiterfassung")
    public String root() {
        return "redirect:/zeiterfassung/";
    }

    // ==================== Dynamic App Routes ====================
    // Matches /zeiterfassung/ and any sub-path that does NOT contain a dot (file
    // extension).
    // This allows React Router to handle all frontend routes (e.g.
    // /zeiterfassung/projects/123)
    // while letting Spring Boot serve static assets (e.g.
    // /zeiterfassung/assets/index.js) directly.

    @GetMapping(value = {
            "/zeiterfassung/",
            "/zeiterfassung/{path:[^\\.]*}",
            "/zeiterfassung/{p1:[^\\.]*}/{p2:[^\\.]*}",
            "/zeiterfassung/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}",
            "/zeiterfassung/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}/{p4:[^\\.]*}",
            "/zeiterfassung/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}/{p4:[^\\.]*}/{p5:[^\\.]*}"
    })
    public String forwardAppRoutes() {
        return FORWARD_TO_INDEX;
    }
}
