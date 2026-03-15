package org.example.kalkulationsprogramm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA-Controller für Client-Side-Routing des PC-Frontends.
 * Leitet alle Frontend-Routen auf index.html weiter,
 * damit React Router die Navigation übernehmen kann.
 * 
 * Hinweis: Die Mobile Zeiterfassungs-PWA (/zeiterfassung/*) wird vom
 * ZeiterfassungController behandelt.
 */
@Controller
public class SpaController {

    /**
     * PC-Frontend Routen - werden auf /index.html weitergeleitet.
     */
    @GetMapping(value = {
            "/",
            "/textbausteine",
            "/textbausteine/**",
            "/leistungen",
            "/leistungen/**",
            "/lieferanten",
            "/lieferanten/**",
            "/artikel",
            "/artikel/**",
            "/arbeitsgaenge",
            "/arbeitsgaenge/**",
            "/produktkategorien",
            "/produktkategorien/**",
            "/projekte",
            "/projekte/**",
            "/anfragen",
            "/anfragen/**",
            "/bestellungen",
            "/bestellungen/**",
            "/analyse",
            "/analyse/**",
            "/formulare",
            "/formulare/**",
            "/offeneposten",
            "/offeneposten/**",
            "/emails",
            "/emails/**",
            "/miete",
            "/miete/**",
            "/document-builder",
            "/document-builder/**",
            "/benutzer",
            "/benutzer/**",
            "/kunden",
            "/kunden/**",
            "/mitarbeiter",
            "/mitarbeiter/**",
            "/urlaubsantraege",
            "/urlaubsantraege/**",
            "/zeitbuchungen",
            "/zeitbuchungen/**",
            "/auswertung",
            "/auswertung/**",
            "/zeitkonten",
            "/zeitkonten/**",
            "/feiertage",
            "/feiertage/**",
            "/abteilung-berechtigungen",
            "/abteilung-berechtigungen/**",
            "/kalender",
            "/kalender/**",
            "/rechnungsuebersicht",
            "/rechnungsuebersicht/**",
            "/steuerberater",
            "/steuerberater/**",
            "/dokument-editor",
            "/dokument-editor/**",
                "/login",
                "/login/**",
                "/onboarding",
                "/onboarding/**",
            "/firma",
            "/firma/**",
            "/arbeitszeitarten",
            "/arbeitszeitarten/**"
    })
    public String forwardPcFrontend() {
        return "forward:/index.html";
    }
}
