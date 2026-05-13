package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.EhegattengehaltSchedulerService;
import org.example.kalkulationsprogramm.service.KasseSaldoService;
import org.example.kalkulationsprogramm.service.KasseShortcutService;
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * REST-Endpoints fuer die Kassenbuch-Shortcuts.
 *
 *   POST /api/buchhaltung/kasse/bank-abhebung
 *   POST /api/buchhaltung/kasse/privateinlage
 *   POST /api/buchhaltung/kasse/privatentnahme
 *   POST /api/buchhaltung/kasse/lohn-zahlung
 *   GET  /api/buchhaltung/kasse/saldo
 *   GET  /api/buchhaltung/kasse/einstellung
 *   PUT  /api/buchhaltung/kasse/einstellung
 *
 * Auth via Session-Cookie (PC) oder ?token=... (Mobile) — delegiert an
 * BelegService.findCaller(), gleiche Strategie wie BelegController.
 */
@Slf4j
@RestController
@RequestMapping("/api/buchhaltung/kasse")
@RequiredArgsConstructor
public class KasseShortcutController {

    private final BelegService belegService;
    private final KasseShortcutService kasseShortcutService;
    private final KasseSaldoService kasseSaldoService;
    private final KasseEinstellungRepository kasseEinstellungRepository;
    private final SachkontoRepository sachkontoRepository;

    @PostMapping("/bank-abhebung")
    public ResponseEntity<?> bankAbhebung(
            @RequestBody BankAbhebungRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Bank-Abhebung ist KASSE_EINNAHME -> erhoeht den Saldo immer.
        // Daher KEIN KasseUnterdeckung-Catch noetig (Reviewer-Hinweis).
        try {
            Beleg b = kasseShortcutService.bankAbhebung(
                    req.betrag(), req.datum(), req.belegNr(), req.beschreibung(), caller);
            return ResponseEntity.ok(belegService.toDto(b));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/privateinlage")
    public ResponseEntity<?> privateinlage(
            @RequestBody EinfacheKasseRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Beleg b = kasseShortcutService.privatEinlage(
                    req.betrag(), req.datum(), req.beschreibung(), caller);
            return ResponseEntity.ok(belegService.toDto(b));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/privatentnahme")
    public ResponseEntity<?> privatentnahme(
            @RequestBody EinfacheKasseRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Beleg b = kasseShortcutService.privatEntnahme(
                    req.betrag(), req.datum(), req.beschreibung(), caller);
            return ResponseEntity.ok(belegService.toDto(b));
        } catch (KasseUnterdeckungException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(saldoFehler(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/lohn-zahlung")
    public ResponseEntity<?> lohnZahlung(
            @RequestBody LohnZahlungRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Buchungskonto ist hart "4120 Loehne & Gehaelter" — Ehegattengehalt
        // ist rein buchhalterisch, der Handwerker soll kein Konto auswaehlen
        // muessen. Keine Kostenstelle.
        Sachkonto sk = sachkontoRepository.findByNummer(EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER)
                .orElse(null);
        if (sk == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", "Lohn-Sachkonto " + EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER
                            + " (Loehne & Gehaelter) fehlt — bitte in Sachkonten anlegen"));
        }
        try {
            KasseShortcutService.LohnZahlungResult result = kasseShortcutService.lohnZahlung(
                    req.betrag(), req.datum(), req.empfaengerName(), sk, null, caller);
            return ResponseEntity.ok(Map.of(
                    "privateinlage", result.privateinlage() != null ? belegService.toDto(result.privateinlage()) : null,
                    "lohnBeleg", belegService.toDto(result.lohnBeleg()),
                    "neuerSaldo", result.neuerSaldo()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/saldo")
    public ResponseEntity<?> getSaldo(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(Map.of(
                "saldo", kasseSaldoService.berechneAktuellenSaldo(),
                "mindestbestand", kasseSaldoService.getMindestbestand()));
    }

    @GetMapping("/einstellung")
    public ResponseEntity<?> getEinstellung(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(kasseEinstellungRepository.findSingleton()
                .map(KasseShortcutController::toEinstellungDto)
                .orElseGet(() -> new EinstellungResponse(
                        null, BigDecimal.ZERO, false, null, null, null, null)));
    }

    @PutMapping("/einstellung")
    public ResponseEntity<?> updateEinstellung(
            @RequestBody EinstellungRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            // V319-Migration legt die Singleton-Zeile beim DB-Start an.
            // Fail-fast, falls sie fehlt — sonst entstehen mehrere Konfig-Rows.
            KasseEinstellung k = kasseEinstellungRepository.findSingleton()
                    .orElseThrow(() -> new IllegalStateException(
                            "Kasse-Einstellung-Singleton fehlt — Migration V319 nicht ausgefuehrt?"));
            if (req.mindestbestand() != null) k.setMindestbestand(req.mindestbestand());
            k.setEhegattengehaltAktiv(Boolean.TRUE.equals(req.ehegattengehaltAktiv()));
            k.setEhegattengehaltBetrag(req.ehegattengehaltBetrag());
            k.setEhegattengehaltTag(req.ehegattengehaltTag());
            k.setEhegattengehaltEmpfaengerName(req.ehegattengehaltEmpfaengerName());
            if (req.privateinlageSachkontoId() != null) {
                k.setPrivateinlageSachkonto(sachkontoRepository.findById(req.privateinlageSachkontoId()).orElse(null));
            } else {
                k.setPrivateinlageSachkonto(null);
            }
            validateEhegattengehaltKonfig(k);
            KasseEinstellung gespeichert = kasseEinstellungRepository.save(k);
            return ResponseEntity.ok(toEinstellungDto(gespeichert));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private void validateEhegattengehaltKonfig(KasseEinstellung k) {
        if (!k.isEhegattengehaltAktiv()) {
            return;
        }
        if (k.getEhegattengehaltBetrag() == null || k.getEhegattengehaltBetrag().signum() <= 0) {
            throw new IllegalArgumentException("Ehegattengehalt: Betrag muss positiv sein");
        }
        if (k.getEhegattengehaltTag() == null || k.getEhegattengehaltTag() < 1 || k.getEhegattengehaltTag() > 28) {
            throw new IllegalArgumentException("Ehegattengehalt: Tag muss zwischen 1 und 28 liegen");
        }
        if (k.getPrivateinlageSachkonto() == null) {
            throw new IllegalArgumentException("Ehegattengehalt: Privateinlage-Sachkonto fehlt (fuer Auto-Auffuellen)");
        }
        // Lohn-Sachkonto "4120" wird zum Buchungszeitpunkt nachgeschlagen.
        // Fail-fast bei Speichern, damit der Operator den Konfig-Fehler frueh sieht.
        if (sachkontoRepository.findByNummer(EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER).isEmpty()) {
            throw new IllegalArgumentException("Ehegattengehalt: Sachkonto "
                    + EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER
                    + " (Loehne & Gehaelter) fehlt in den Sachkonten");
        }
    }

    private Map<String, Object> saldoFehler(KasseUnterdeckungException e) {
        return Map.of(
                "message", e.getMessage(),
                "projizierterSaldo", e.getProjizierterSaldo(),
                "mindestbestand", e.getMindestbestand());
    }

    private static EinstellungResponse toEinstellungDto(KasseEinstellung k) {
        return new EinstellungResponse(
                k.getId(),
                k.getMindestbestand(),
                k.isEhegattengehaltAktiv(),
                k.getEhegattengehaltBetrag(),
                k.getEhegattengehaltTag(),
                k.getEhegattengehaltEmpfaengerName(),
                k.getPrivateinlageSachkonto() != null ? k.getPrivateinlageSachkonto().getId() : null);
    }

    // ===================== Request / Response DTOs =====================

    public record BankAbhebungRequest(BigDecimal betrag, LocalDate datum, String belegNr, String beschreibung) {}

    public record EinfacheKasseRequest(BigDecimal betrag, LocalDate datum, String beschreibung) {}

    public record LohnZahlungRequest(BigDecimal betrag, LocalDate datum, String empfaengerName) {}

    public record EinstellungRequest(
            BigDecimal mindestbestand,
            Boolean ehegattengehaltAktiv,
            BigDecimal ehegattengehaltBetrag,
            Integer ehegattengehaltTag,
            String ehegattengehaltEmpfaengerName,
            Long privateinlageSachkontoId) {}

    public record EinstellungResponse(
            Long id,
            BigDecimal mindestbestand,
            boolean ehegattengehaltAktiv,
            BigDecimal ehegattengehaltBetrag,
            Integer ehegattengehaltTag,
            String ehegattengehaltEmpfaengerName,
            Long privateinlageSachkontoId) {}
}
