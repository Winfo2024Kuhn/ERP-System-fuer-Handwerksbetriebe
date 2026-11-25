package org.example.kalkulationsprogramm.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerRequestDto;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ansprechpartner eines Lieferanten (Reiter „Ansprechpartner“). Wie alle
 * {@code /api/lieferanten/**}-Endpunkte für angemeldete Büro-Nutzer (Session + CSRF);
 * die Handy-App darf sie nicht aufrufen (nicht in {@code MobileApiPolicy}).
 */
@RestController
@RequestMapping("/api/lieferanten/{lieferantId}/ansprechpartner")
@Validated
@RequiredArgsConstructor
public class LieferantAnsprechpartnerController {

    private static final String UNGUELTIGE_ID = "Ungültige ID.";

    private final LieferantAnsprechpartnerService service;

    @GetMapping
    public List<LieferantAnsprechpartnerDto> liste(@PathVariable @Positive(message = UNGUELTIGE_ID) Long lieferantId) {
        return service.liste(lieferantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LieferantAnsprechpartnerDto anlegen(@PathVariable @Positive(message = UNGUELTIGE_ID) Long lieferantId,
                                               @RequestBody LieferantAnsprechpartnerRequestDto eingabe) {
        return service.anlegen(lieferantId, eingabe);
    }

    @PutMapping("/{ansprechpartnerId}")
    public LieferantAnsprechpartnerDto aendern(@PathVariable @Positive(message = UNGUELTIGE_ID) Long lieferantId,
                                               @PathVariable @Positive(message = UNGUELTIGE_ID) Long ansprechpartnerId,
                                               @RequestBody LieferantAnsprechpartnerRequestDto eingabe) {
        return service.aendern(lieferantId, ansprechpartnerId, eingabe);
    }

    @DeleteMapping("/{ansprechpartnerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(@PathVariable @Positive(message = UNGUELTIGE_ID) Long lieferantId,
                         @PathVariable @Positive(message = UNGUELTIGE_ID) Long ansprechpartnerId) {
        service.loeschen(lieferantId, ansprechpartnerId);
    }
}
