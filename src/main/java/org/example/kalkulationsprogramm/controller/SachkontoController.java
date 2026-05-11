package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;
import org.example.kalkulationsprogramm.dto.SachkontoDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.BelegeKasseExportPdfService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-Endpoints für Sachkonten + Auswertung.
 *
 * Zugriff: Mitarbeiter benötigt die bestehende BELEG-Sehen-Berechtigung
 * (verwaltet unter Administration → Lieferanten-Dokumentenrechte).
 */
@Slf4j
@RestController
@RequestMapping("/api/buchhaltung")
@RequiredArgsConstructor
public class SachkontoController {

    private final SachkontoRepository sachkontoRepository;
    private final BelegRepository belegRepository;
    private final BelegService belegService;
    private final BelegeKasseExportPdfService belegeKasseExportPdfService;

    private Mitarbeiter resolveCaller(String token, Authentication auth) {
        return belegService.findCaller(token, auth);
    }

    @GetMapping("/sachkonten")
    public ResponseEntity<List<SachkontoDto.Response>> list(
            @RequestParam(value = "nurAktive", defaultValue = "true") boolean nurAktive,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Sachkonto> konten = nurAktive
                ? sachkontoRepository.findByAktivTrueOrderBySortierungAscBezeichnungAsc()
                : sachkontoRepository.findAllByOrderBySortierungAscBezeichnungAsc();
        return ResponseEntity.ok(konten.stream().map(SachkontoController::toDto).toList());
    }

    @PostMapping("/sachkonten")
    @Transactional
    public ResponseEntity<?> create(@RequestBody SachkontoDto.UpsertRequest req, Authentication auth) {
        Mitarbeiter caller = resolveCaller(null, auth);
        // Anlegen veraendert den Kontenrahmen (GoBD-relevant) — nur Validatoren (darfScannen),
        // nicht read-only Konten.
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (req.getBezeichnung() == null || req.getBezeichnung().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bezeichnung fehlt"));
        }
        Sachkonto sk = new Sachkonto();
        applyUpsert(sk, req);
        sachkontoRepository.save(sk);
        return ResponseEntity.ok(toDto(sk));
    }

    @PutMapping("/sachkonten/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody SachkontoDto.UpsertRequest req,
                                    Authentication auth) {
        Mitarbeiter caller = resolveCaller(null, auth);
        // Umbenennen/Deaktivieren veraendert die Auswertungen rueckwirkend — siehe create().
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Sachkonto sk = sachkontoRepository.findById(id).orElse(null);
        if (sk == null) return ResponseEntity.notFound().build();
        applyUpsert(sk, req);
        sachkontoRepository.save(sk);
        return ResponseEntity.ok(toDto(sk));
    }

    @GetMapping("/auswertung")
    public ResponseEntity<SachkontoDto.AuswertungResponse> auswertung(
            @RequestParam(value = "von", required = false) String von,
            @RequestParam(value = "bis", required = false) String bis,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        LocalDate vonDate = parseDate(von);
        LocalDate bisDate = parseDate(bis);

        // Iteriere über validierte Belege im Zeitraum, summiere pro Sachkonto.
        // Belege ohne Sachkonto landen in einer Sammelzeile.
        List<Beleg> alle = belegRepository.findByStatusOrderByUploadDatumDesc(BelegStatus.VALIDIERT);

        Map<Long, BigDecimal> summen = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        BigDecimal ohneKonto = BigDecimal.ZERO;
        int ohneKontoAnzahl = 0;

        for (Beleg b : alle) {
            LocalDate d = b.getBelegDatum();
            if (vonDate != null && (d == null || d.isBefore(vonDate))) continue;
            if (bisDate != null && (d == null || d.isAfter(bisDate))) continue;
            BigDecimal brutto = b.getBetragBrutto() != null ? b.getBetragBrutto() : BigDecimal.ZERO;
            if (b.getSachkonto() == null) {
                ohneKonto = ohneKonto.add(brutto);
                ohneKontoAnzahl++;
            } else {
                Long key = b.getSachkonto().getId();
                summen.merge(key, brutto, BigDecimal::add);
                counts.merge(key, 1, Integer::sum);
            }
        }

        List<SachkontoDto.AuswertungZeile> zeilen = new ArrayList<>();
        BigDecimal sumAufwand = BigDecimal.ZERO;
        BigDecimal sumErtrag = BigDecimal.ZERO;
        BigDecimal sumPrivat = BigDecimal.ZERO;

        for (Sachkonto sk : sachkontoRepository.findAllByOrderBySortierungAscBezeichnungAsc()) {
            BigDecimal s = summen.get(sk.getId());
            if (s == null) continue; // nur Konten mit Bewegung
            zeilen.add(SachkontoDto.AuswertungZeile.builder()
                    .sachkontoId(sk.getId())
                    .nummer(sk.getNummer())
                    .bezeichnung(sk.getBezeichnung())
                    .kontoTyp(sk.getKontoTyp().name())
                    .summe(s.setScale(2, RoundingMode.HALF_UP))
                    .anzahlBelege(counts.getOrDefault(sk.getId(), 0))
                    .build());
            switch (sk.getKontoTyp()) {
                case AUFWAND -> sumAufwand = sumAufwand.add(s);
                case ERTRAG -> sumErtrag = sumErtrag.add(s);
                case PRIVAT -> sumPrivat = sumPrivat.add(s);
                case NEUTRAL -> { /* nicht in GuV-Summen */ }
            }
        }

        if (ohneKonto.signum() != 0) {
            zeilen.add(SachkontoDto.AuswertungZeile.builder()
                    .sachkontoId(null)
                    .bezeichnung("(Noch keinem Konto zugeordnet)")
                    .kontoTyp(null)
                    .summe(ohneKonto.setScale(2, RoundingMode.HALF_UP))
                    .anzahlBelege(ohneKontoAnzahl)
                    .build());
        }

        return ResponseEntity.ok(SachkontoDto.AuswertungResponse.builder()
                .von(vonDate != null ? vonDate.toString() : null)
                .bis(bisDate != null ? bisDate.toString() : null)
                .summeAufwand(sumAufwand.setScale(2, RoundingMode.HALF_UP))
                .summeErtrag(sumErtrag.setScale(2, RoundingMode.HALF_UP))
                .summePrivat(sumPrivat.setScale(2, RoundingMode.HALF_UP))
                .summeOhneKonto(ohneKonto.setScale(2, RoundingMode.HALF_UP))
                .zeilen(zeilen)
                .build());
    }

    /**
     * Monatsexport für den Steuerberater: ein PDF mit allen validierten Belegen
     * eines Kalendermonats inklusive Sachkonto-Auswertung. Wird zusammen mit dem
     * Ordner der hochgeladenen Belegfotos übergeben.
     */
    @GetMapping("/auswertung/monat/pdf")
    public ResponseEntity<?> auswertungMonatPdf(
            @RequestParam("jahr") int jahr,
            @RequestParam("monat") int monat,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (monat < 1 || monat > 12 || jahr < 2000 || jahr > 2999) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ungültiger Monat oder Jahr"));
        }
        try {
            java.nio.file.Path pdfPath = belegeKasseExportPdfService.generatePdf(jahr, monat);
            UrlResource resource = new UrlResource(pdfPath.toUri());
            String filename = String.format("Belege-Kasse-%04d-%02d.pdf", jahr, monat);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Belege-Monatsexport fehlgeschlagen", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "PDF-Erzeugung fehlgeschlagen"));
        }
    }

    // ===================== Helpers =====================

    private static void applyUpsert(Sachkonto sk, SachkontoDto.UpsertRequest req) {
        if (req.getNummer() != null) sk.setNummer(req.getNummer().isBlank() ? null : req.getNummer().trim());
        if (req.getBezeichnung() != null) sk.setBezeichnung(req.getBezeichnung().trim());
        if (req.getKontoTyp() != null) {
            try { sk.setKontoTyp(SachkontoTyp.valueOf(req.getKontoTyp())); }
            catch (IllegalArgumentException ignored) { /* belassen */ }
        }
        if (req.getBeschreibung() != null) sk.setBeschreibung(req.getBeschreibung());
        if (req.getAktiv() != null) sk.setAktiv(req.getAktiv());
        if (req.getSortierung() != null) sk.setSortierung(req.getSortierung());
    }

    private static SachkontoDto.Response toDto(Sachkonto sk) {
        return SachkontoDto.Response.builder()
                .id(sk.getId())
                .nummer(sk.getNummer())
                .bezeichnung(sk.getBezeichnung())
                .kontoTyp(sk.getKontoTyp() != null ? sk.getKontoTyp().name() : null)
                .beschreibung(sk.getBeschreibung())
                .aktiv(sk.isAktiv())
                .sortierung(sk.getSortierung())
                .build();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }
}
