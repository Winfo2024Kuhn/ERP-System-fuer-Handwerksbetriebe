package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Email.EmailReiterSeiteDto;
import org.example.kalkulationsprogramm.service.mail.reiter.EmailReiterService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * E-Mail-Reiter der Detailseiten (Projekt, Anfrage, Kunde, Lieferant): Verläufe seitenweise, mit
 * Suche und Richtung. Regeln, Grenzen und Fehlermeldungen: {@link EmailReiterService}.
 */
@RestController
@RequestMapping("/api/emails/reiter")
@RequiredArgsConstructor
public class EmailReiterController {

    private final EmailReiterService reiterService;

    /** {@code GET /api/emails/reiter/{bezug}/{id}?page=0&size=25&q=…&richtung=IN|OUT}. */
    @GetMapping("/{bezug}/{id}")
    public EmailReiterSeiteDto seite(
            @PathVariable String bezug,
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "richtung", required = false) String richtung,
            Authentication authentication) {
        return reiterService.seite(bezug, id, page, size, q, richtung, authentication);
    }
}
