package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungErledigtRequest;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungKategorienErledigtRequest;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungZusammenfassungDto;
import org.example.kalkulationsprogramm.service.benachrichtigung.BenachrichtigungService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Benachrichtigungs-Glocke: Inhalt laden und Einträge erledigen. Was erledigt ist, merkt sich der
 * Server pro Benutzer – so bleibt es auch nach Neuladen, im neuen Tab und am anderen PC weg.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final BenachrichtigungService benachrichtigungService;

    /**
     * @param alle {@code true} für die Seite „Benachrichtigungen“ (viele Einträge je Kategorie),
     *             sonst die Kurzfassung für die Glocke
     */
    @GetMapping("/summary")
    public BenachrichtigungZusammenfassungDto getSummary(@RequestParam(required = false) Long mitarbeiterId,
            @RequestParam(defaultValue = "false") boolean alle, Authentication authentication) {
        return benachrichtigungService.zusammenfassung(mitarbeiterId, authentication, alle);
    }

    /** Einzelne Einträge erledigen; Mails werden dabei als gelesen markiert. */
    @PostMapping("/erledigt")
    public ResponseEntity<Void> erledigen(@Valid @RequestBody BenachrichtigungErledigtRequest request,
            Authentication authentication) {
        try {
            benachrichtigungService.erledigen(request.ids(), authentication);
        } catch (BenachrichtigungService.NichtAngemeldetException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** Ganze Spalten erledigen – alle Einträge der Kategorien, nicht nur die angezeigten. */
    @PostMapping("/erledigt/kategorien")
    public Map<String, Integer> kategorienErledigen(
            @Valid @RequestBody BenachrichtigungKategorienErledigtRequest request, Authentication authentication) {
        if (!BenachrichtigungService.KATEGORIEN.containsAll(request.kategorien())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diese Kategorie gibt es nicht.");
        }
        try {
            return Map.of("erledigt", benachrichtigungService.kategorienErledigen(request.kategorien(), authentication));
        } catch (BenachrichtigungService.NichtAngemeldetException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }
}
