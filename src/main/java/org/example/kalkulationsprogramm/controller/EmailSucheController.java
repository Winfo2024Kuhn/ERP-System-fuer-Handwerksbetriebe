package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Email.EmailSuchErgebnis;
import org.example.kalkulationsprogramm.service.mail.suche.EmailSucheService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * E-Mail-Suche des E-Mail-Centers: in einem Ordner oder über alle Ordner, seitenweise.
 * Regeln und Grenzen: {@link EmailSucheService}.
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailSucheController {

    private final EmailSucheService sucheService;

    /**
     * {@code GET /api/emails/suche?q=…&ordner=…&page=0&size=25}. {@code q} prüft der Service (eigene
     * Meldung statt der allgemeinen „Parameter fehlt“), {@code ordner} leer = alle Ordner.
     */
    @GetMapping("/suche")
    public EmailSuchErgebnis suche(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "ordner", required = false) String ordner,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            Authentication authentication) {
        return sucheService.suche(q, ordner, page, size, authentication);
    }
}
