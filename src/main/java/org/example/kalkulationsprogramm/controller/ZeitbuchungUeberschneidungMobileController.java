package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.UeberschneidungMeldungDto;
import org.example.kalkulationsprogramm.service.ZeitbuchungUeberschneidungService;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungAnzeige;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Überschneidungs-Meldungen in den Nachrichten der Handy-App: Eine Handy-Buchung hatte
 * Vorrang vor einem Eintrag im Büro. Nur die eigenen Meldungen des angemeldeten
 * Mitarbeiters (Login-Token, {@link MobilePrincipal}).
 */
@RestController
@RequestMapping("/api/zeiterfassung/ueberschneidungen")
@RequiredArgsConstructor
public class ZeitbuchungUeberschneidungMobileController {

    private final ZeitbuchungUeberschneidungService service;

    /** Noch nicht gelesene eigene Meldungen, neueste zuerst. */
    @GetMapping
    public List<UeberschneidungMeldungDto> ungelesene() {
        Long mitarbeiterId = eigenerMitarbeiter();
        return service.ungeleseneMitAnzeige(mitarbeiterId).stream()
                .map(m -> alsDto(m.meldung(), m.anzeige()))
                .toList();
    }

    /** „Gelesen“ an einer eigenen Meldung. */
    @PutMapping("/{id}/gesehen")
    public ResponseEntity<Void> gesehen(@PathVariable Long id) {
        Long mitarbeiterId = eigenerMitarbeiter();
        if (id == null || id <= 0 || !service.alsGesehenMarkieren(id, mitarbeiterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Meldung gibt es nicht.");
        }
        return ResponseEntity.noContent().build();
    }

    private static Long eigenerMitarbeiter() {
        MobilePrincipal mobile = MobilePrincipal.current();
        if (mobile == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte in der Zeiterfassung anmelden.");
        }
        return mobile.mitarbeiterId();
    }

    private static UeberschneidungMeldungDto alsDto(ZeitbuchungUeberschneidung u,
            UeberschneidungAnzeige.Anzeige anzeige) {
        return new UeberschneidungMeldungDto(u.getId(), u.getHandyVon().toString(),
                u.getHandyBis() != null ? u.getHandyBis().toString() : null,
                anzeige.titel(), anzeige.text(), u.getErstelltAm().toString());
    }
}
