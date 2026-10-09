package org.example.kalkulationsprogramm.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentPositionenDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZuordnungService;
import org.example.kalkulationsprogramm.service.PositionenNichtLesbarException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Positionen eines Lieferanten-Dokuments und die Projektaufteilung nach
 * Positionen (Bestellübersicht → "Projekt zuordnen").
 *
 * <p>{@code geschaeftsdokumentId} ist zugleich die ID des Lieferanten-Dokuments
 * ({@code @MapsId}).
 */
@Validated
@RestController
@RequestMapping("/api/bestellungen-uebersicht/positionen")
@RequiredArgsConstructor
public class LieferantDokumentPositionController {

    private final LieferantDokumentZuordnungService zuordnungService;
    private final GeminiDokumentAnalyseService analyseService;
    private final BelegService belegService;
    private final LieferantDokumentZugriffService zugriffService;

    /** Positionen mit ihrem aktuellen Ziel. */
    @GetMapping("/{geschaeftsdokumentId}")
    public ResponseEntity<?> positionen(@PathVariable @Positive Long geschaeftsdokumentId,
            @RequestParam(value = "token", required = false) String token, Authentication auth) {
        ResponseEntity<?> abgelehnt = zugriffPruefen(geschaeftsdokumentId, token, auth);
        if (abgelehnt != null) {
            return abgelehnt;
        }
        try {
            return ResponseEntity.ok(zuordnungService.positionsUebersicht(geschaeftsdokumentId));
        } catch (NoSuchElementException e) {
            return nichtGefunden(e);
        }
    }

    /**
     * Liest die Positionen per KI (nach), z. B. für ältere Dokumente. Dauert je
     * nach Dokument bis zu einer Minute.
     */
    @PostMapping("/{geschaeftsdokumentId}/auslesen")
    public ResponseEntity<?> auslesen(@PathVariable @Positive Long geschaeftsdokumentId,
            @RequestParam(value = "token", required = false) String token, Authentication auth) {
        ResponseEntity<?> abgelehnt = zugriffPruefen(geschaeftsdokumentId, token, auth);
        if (abgelehnt != null) {
            return abgelehnt;
        }
        try {
            analyseService.positionenNachlesen(geschaeftsdokumentId);
            return ResponseEntity.ok(zuordnungService.positionsUebersicht(geschaeftsdokumentId));
        } catch (NoSuchElementException e) {
            return nichtGefunden(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (PositionenNichtLesbarException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    /** Rechnet die Aufteilung durch, ohne zu speichern. */
    @PostMapping("/{geschaeftsdokumentId}/vorschau")
    public ResponseEntity<?> vorschau(@PathVariable @Positive Long geschaeftsdokumentId,
            @Valid @RequestBody DokumentPositionenDto.AufteilungRequest request,
            @RequestParam(value = "token", required = false) String token, Authentication auth) {
        ResponseEntity<?> abgelehnt = zugriffPruefen(geschaeftsdokumentId, token, auth);
        if (abgelehnt != null) {
            return abgelehnt;
        }
        try {
            return ResponseEntity.ok(zuordnungService.vorschau(geschaeftsdokumentId, request.positionen()));
        } catch (NoSuchElementException e) {
            return nichtGefunden(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Speichert die Aufteilung nach Positionen. */
    @PostMapping("/{geschaeftsdokumentId}/zuordnen")
    public ResponseEntity<?> zuordnen(@PathVariable @Positive Long geschaeftsdokumentId,
            @Valid @RequestBody DokumentPositionenDto.AufteilungRequest request,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        ResponseEntity<?> abgelehnt = zugriffPruefen(geschaeftsdokumentId, token, auth);
        if (abgelehnt != null) {
            return abgelehnt;
        }
        FrontendUserProfile zugeordnetVon = zuordnungService.zugeordnetVon(belegService.findCaller(token, auth), auth);
        try {
            int anzahl = zuordnungService.speichereNachPositionen(geschaeftsdokumentId, request, zugeordnetVon);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Erfolgreich " + anzahl + " Zuordnung(en) gespeichert",
                    "zuordnungen", anzahl));
        } catch (NoSuchElementException e) {
            return nichtGefunden(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 401 ohne Anmeldung, 404 wenn das Dokument unbekannt oder für den Aufrufer nicht sichtbar ist,
     * sonst {@code null}.
     */
    private ResponseEntity<?> zugriffPruefen(Long dokumentId, String token, Authentication auth) {
        var sichtbareTypen = zugriffService.sichtbareTypen(token, auth);
        if (sichtbareTypen.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!zugriffService.istSichtbar(dokumentId, sichtbareTypen.get())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Dokument nicht gefunden"));
        }
        return null;
    }

    private static ResponseEntity<Map<String, String>> nichtGefunden(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
