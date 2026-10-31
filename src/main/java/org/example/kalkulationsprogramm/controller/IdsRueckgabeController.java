package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

import org.example.kalkulationsprogramm.service.ids.IdsRueckgabe;
import org.example.kalkulationsprogramm.service.ids.IdsShopBesuchService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;

/**
 * Rücksprung aus dem Lieferanten-Shop: Der Shop schickt den Warenkorb über den Browser hierher
 * (Feld {@code warenkorb}, meist {@code multipart/form-data}). Ohne Anmeldung erreichbar, Ausweis ist der
 * Schlüssel im Pfad (siehe {@code SecurityConfig#idsRueckgabeFilterChain}). Danach landet der Browser
 * auf einer Seite des Programms, die erklärt, wie es weitergeht.
 */
@RestController
@RequiredArgsConstructor
public class IdsRueckgabeController {

    static final String FELD = "warenkorb";
    static final String ZIELSEITE = "/ids/warenkorb-angekommen";

    private final IdsShopBesuchService shopBesuch;

    @PostMapping("/api/ids/rueckgabe/{schluessel}")
    public ResponseEntity<Void> rueckgabe(@PathVariable String schluessel, HttpServletRequest request) {
        IdsShopBesuchService.Rueckgabe ergebnis = shopBesuch.nimmRueckgabeAn(schluessel, lies(request));
        UriComponentsBuilder ziel = UriComponentsBuilder.fromPath(ZIELSEITE)
                .queryParam("ergebnis", ergebnis.ergebnis().name().toLowerCase(Locale.ROOT));
        if (ergebnis.vorgangId() != null) {
            ziel.queryParam("vorgang", ergebnis.vorgangId());
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(ziel.build().toUriString()))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /** Multipart: Rohbytes (das XML nennt seine Kodierung selbst); sonst das Textfeld des Formulars. */
    private static IdsRueckgabe lies(HttpServletRequest request) {
        String typ = request.getContentType();
        if (typ != null && typ.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            try {
                Part teil = request.getPart(FELD);
                if (teil != null) {
                    try (InputStream in = teil.getInputStream()) {
                        return new IdsRueckgabe(in.readAllBytes(), null);
                    }
                }
            } catch (IOException | ServletException | IllegalStateException e) {
                return new IdsRueckgabe(null, null);
            }
        }
        return new IdsRueckgabe(null, request.getParameter(FELD));
    }
}
