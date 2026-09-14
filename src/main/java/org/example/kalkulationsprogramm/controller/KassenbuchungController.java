package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenbuchungDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.KassenbuchungService;
import org.example.kalkulationsprogramm.service.KassenbuchGesperrtException;
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/buchhaltung/kassenbuch")
@RequiredArgsConstructor
public class KassenbuchungController {
    private final BelegService belegService;
    private final KassenbuchungService kassenbuchungService;

    @PostMapping(value = "/buchungen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> buche(
            @RequestPart("daten") KassenbuchungDto.CreateRequest req,
            @RequestPart(value = "datei", required = false) MultipartFile datei,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(belegService.toDto(kassenbuchungService.buche(req, datei, caller)));
        } catch (KassenbuchGesperrtException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", e.getMessage(), "hinweis", e.getLoesungshinweis() == null ? "" : e.getLoesungshinweis()));
        } catch (KasseUnterdeckungException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", e.getMessage(), "projizierterSaldo", e.getProjizierterSaldo(),
                    "mindestbestand", e.getMindestbestand()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Anlegen fehlgeschlagen" : e.getReason()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Kassenbuchung anlegen fehlgeschlagen", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Anlegen fehlgeschlagen"));
        }
    }

    @GetMapping("/offene-ausgangsrechnungen")
    public ResponseEntity<?> offeneAusgangsrechnungen(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(kassenbuchungService.offeneAusgangsrechnungen());
        } catch (Exception e) {
            log.error("Offene Kundenrechnungen laden fehlgeschlagen", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Laden fehlgeschlagen"));
        }
    }
}
