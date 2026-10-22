package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.AufschlagBerechtigungService;
import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.AufschlagService.Uebersicht;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Aufschläge einstellen: allgemein und je Kategorie. Der Aufschlag am Artikel bleibt am Artikel. Ansehen und Ändern darf nur, wer das Recht
 * „Aufschläge ändern“ hat (Administratoren immer); {@code /berechtigung} darf jeder angemeldete Benutzer fragen, damit die Oberfläche den Knopf zeigt oder nicht.
 */
@RestController
@RequestMapping("/api/aufschlaege")
@RequiredArgsConstructor
public class AufschlagController {

    private final AufschlagService service;
    private final AufschlagBerechtigungService berechtigung;

    /** {@code prozent} leer = kein Aufschlag eingestellt. */
    public record Eingabe(BigDecimal prozent) { }

    /** Darf der angemeldete Benutzer die Aufschläge ändern? */
    public record Berechtigung(boolean darfAendern) { }

    @GetMapping("/berechtigung")
    public Berechtigung berechtigung(Authentication authentication) {
        return new Berechtigung(berechtigung.darfAendern(authentication));
    }

    @GetMapping
    public Uebersicht uebersicht(Authentication authentication) {
        berechtigung.verlange(authentication);
        return service.uebersicht();
    }

    @PutMapping("/allgemein")
    public Uebersicht allgemein(@RequestBody Eingabe eingabe, Authentication authentication) {
        berechtigung.verlange(authentication);
        return service.setzeAllgemein(eingabe == null ? null : eingabe.prozent());
    }

    @PutMapping("/kategorien/{id}")
    public Uebersicht kategorie(@PathVariable Integer id, @RequestBody Eingabe eingabe, Authentication authentication) {
        berechtigung.verlange(authentication);
        return service.setzeKategorie(id, eingabe == null ? null : eingabe.prozent());
    }
}
