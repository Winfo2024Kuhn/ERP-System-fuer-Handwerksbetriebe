package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Kontakt;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.LieferantEinkaufKontaktService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lieferanten/{lieferantId}/einkauf-kontakte")
@RequiredArgsConstructor
public class LieferantEinkaufKontaktController {
    private final LieferantEinkaufKontaktService service;
    private final EinkaufBerechtigungService berechtigungService;

    @GetMapping
    public List<Kontakt> liste(@PathVariable Long lieferantId, Authentication auth) {
        berechtigungService.verlange(auth, EinkaufBerechtigung.LESEN);
        try { return service.liste(lieferantId); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Kontakt anlegen(@PathVariable Long lieferantId, @Valid @RequestBody Kontakt request, Authentication auth) {
        Long akteurId = berechtigungService.verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        if (request.id() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein neuer Kontakt darf keine ID enthalten.");
        try { return service.speichern(lieferantId, request, akteurId); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @PutMapping("/{kontaktId}")
    public Kontakt aktualisieren(@PathVariable Long lieferantId, @PathVariable Long kontaktId,
            @Valid @RequestBody Kontakt request, Authentication auth) {
        Long akteurId = berechtigungService.verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        if (request.id() == null || !request.id().equals(kontaktId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Kontakt-ID in der Adresse und im Datensatz müssen übereinstimmen.");
        }
        try { return service.speichern(lieferantId, request, akteurId); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
    }
}
