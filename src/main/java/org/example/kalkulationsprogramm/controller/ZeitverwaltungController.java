package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.example.kalkulationsprogramm.service.ZeitbuchungAuditService;
import org.example.kalkulationsprogramm.service.ZeitkontoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Controller für die Zeiterfassungs-Verwaltung im PC-Frontend.
 * Nicht zu verwechseln mit ZeiterfassungApiController (für PWA).
 */
@RestController
@RequestMapping("/api/zeitverwaltung")
@RequiredArgsConstructor
public class ZeitverwaltungController {

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final FeiertagService feiertagService;
    private final ZeitkontoService zeitkontoService;
    private final org.example.kalkulationsprogramm.service.ProjektAuswertungPdfService projektAuswertungPdfService;
    private final org.example.kalkulationsprogramm.repository.ProjektRepository projektRepository;
    private final org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    private final org.example.kalkulationsprogramm.repository.ArbeitsgangRepository arbeitsgangRepository;
    private final ZeitbuchungAuditService auditService;
    private final org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository frontendUserProfileRepository;
    private final org.example.kalkulationsprogramm.service.MonatsSaldoService monatsSaldoService;
    private final org.example.kalkulationsprogramm.service.MonatsSaldoWarmupService monatsSaldoWarmupService;

    // ==================== Zeitbuchungen ====================

    /**
     * Gibt alle Zeitbuchungen für einen Mitarbeiter in einem Monat zurück.
     */
    @GetMapping("/buchungen")
    public ResponseEntity<List<Map<String, Object>>> getBuchungen(
            @RequestParam Long mitarbeiterId,
            @RequestParam int jahr,
            @RequestParam int monat) {

        LocalDateTime start = LocalDateTime.of(jahr, monat, 1, 0, 0);
        LocalDateTime end = YearMonth.of(jahr, monat).atEndOfMonth().atTime(23, 59, 59);

        List<Zeitbuchung> buchungen = zeitbuchungRepository
                .findByMitarbeiterIdAndStartZeitAfter(mitarbeiterId, start);

        // Filter für den Monat
        List<Map<String, Object>> result = buchungen.stream()
                .filter(b -> b.getStartZeit() != null &&
                        b.getStartZeit().isBefore(end) &&
                        b.getStartZeit().isAfter(start.minusSeconds(1)))
                .map(this::buchungToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Aktualisiert eine Zeitbuchung (GoBD-konform mit Audit-Trail).
     * Erwartet: bearbeiterId und aenderungsgrund im Request-Body.
     */
    @PutMapping("/buchungen/{id}")
    public ResponseEntity<?> updateBuchung(
            @PathVariable Long id,
            @RequestBody Map<String, Object> data) {

        // Bearbeiter ermitteln
        Long bearbeiterId = data.get("bearbeiterId") != null
                ? Long.valueOf(data.get("bearbeiterId").toString())
                : null;
        String aenderungsgrund = (String) data.get("aenderungsgrund");

        // Validierung: Änderungsgrund ist Pflicht für GoBD-Konformität
        if (aenderungsgrund == null || aenderungsgrund.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Änderungsgrund ist ein Pflichtfeld"));
        }

        Mitarbeiter bearbeiter = null;
        if (bearbeiterId != null) {
            bearbeiter = mitarbeiterRepository.findById(bearbeiterId).orElse(null);
        }
        if (bearbeiter == null) {
            // Fallback: Versuche über FrontendUserProfile
            bearbeiter = frontendUserProfileRepository.findAll().stream()
                    .filter(p -> p.getMitarbeiter() != null)
                    .map(p -> p.getMitarbeiter())
                    .findFirst()
                    .orElse(null);
        }
        if (bearbeiter == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bearbeiter konnte nicht ermittelt werden"));
        }

        Zeitbuchung buchung = zeitbuchungRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buchung nicht gefunden: " + id));

        // Änderungen anwenden
        if (data.containsKey("startZeit")) {
            buchung.setStartZeit(LocalDateTime.parse((String) data.get("startZeit")));
        }
        if (data.containsKey("endeZeit")) {
            String endeZeitStr = (String) data.get("endeZeit");
            buchung.setEndeZeit(endeZeitStr != null ? LocalDateTime.parse(endeZeitStr) : null);
        }
        if (data.containsKey("notiz")) {
            buchung.setNotiz((String) data.get("notiz"));
        }

        // Projekt aktualisieren
        if (data.containsKey("projektId") && data.get("projektId") != null) {
            Long projektId = Long.valueOf(data.get("projektId").toString());
            org.example.kalkulationsprogramm.domain.Projekt neuesProjekt = projektRepository.findById(projektId)
                    .orElseThrow(() -> new IllegalArgumentException("Projekt nicht gefunden: " + projektId));
            buchung.setProjekt(neuesProjekt);
            // Bei Projektwechsel: Produktkategorie zurücksetzen (wird unten ggf. neu
            // gesetzt)
            buchung.setProjektProduktkategorie(null);
        }

        // Arbeitsgang aktualisieren
        if (data.containsKey("arbeitsgangId") && data.get("arbeitsgangId") != null) {
            Long arbeitsgangId = Long.valueOf(data.get("arbeitsgangId").toString());
            int buchungsJahr = buchung.getStartZeit() != null 
                    ? buchung.getStartZeit().getYear() 
                    : java.time.LocalDate.now().getYear();
            
            // Stundensatz für Buchungsjahr suchen, Fallback: nächstes verfügbares Jahr
            Optional<org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz> stundensatz = 
                    arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, buchungsJahr);
            if (stundensatz.isEmpty()) {
                // Fallback: Nächster verfügbarer Stundensatz (Jahr >= Buchungsjahr)
                stundensatz = arbeitsgangStundensatzRepository
                        .findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(arbeitsgangId, buchungsJahr);
            }
            if (stundensatz.isEmpty()) {
                // Letzter Fallback: Neuester Stundensatz überhaupt
                stundensatz = arbeitsgangStundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgangId);
            }
            stundensatz.ifPresent(buchung::setArbeitsgangStundensatz);
            
            // Direkten Arbeitsgang auch immer setzen
            arbeitsgangRepository.findById(arbeitsgangId)
                    .ifPresent(buchung::setArbeitsgang);
        }

        // anzahlInStunden neu berechnen wenn Start- und Endezeit vorhanden
        if (buchung.getStartZeit() != null && buchung.getEndeZeit() != null) {
            java.time.Duration dauer = java.time.Duration.between(buchung.getStartZeit(), buchung.getEndeZeit());
            BigDecimal stunden = BigDecimal.valueOf(dauer.toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            buchung.setAnzahlInStunden(stunden);
        }

        // Produktkategorie setzen (NACH dem Projektwechsel!)
        if (data.containsKey("produktkategorieId")) {
            Object pkId = data.get("produktkategorieId");
            if (pkId != null) {
                Long produktkategorieId = Long.valueOf(pkId.toString());
                // Finde passendes ProjektProduktkategorie über das (ggf. neue) Projekt
                if (buchung.getProjekt() != null && buchung.getProjekt().getProjektProduktkategorien() != null) {
                    buchung.getProjekt().getProjektProduktkategorien().stream()
                            .filter(pk -> pk.getProduktkategorie() != null &&
                                    pk.getProduktkategorie().getId().equals(produktkategorieId))
                            .findFirst()
                            .ifPresentOrElse(
                                    buchung::setProjektProduktkategorie,
                                    () -> buchung.setProjektProduktkategorie(null));
                }
            } else {
                buchung.setProjektProduktkategorie(null);
            }
        }

        // WICHTIG: Erst Version erhöhen, dann Audit protokollieren!
        // Sonst Unique-Constraint-Fehler (Version 1 existiert bereits)
        buchung.markiereAlsGeaendert(bearbeiter);

        // AUDIT: Zustand mit NEUER Version protokollieren
        auditService.protokolliereAenderung(buchung, bearbeiter, ErfassungsQuelle.DESKTOP, aenderungsgrund);

        Zeitbuchung saved = zeitbuchungRepository.save(buchung);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDateTime(
                buchung.getMitarbeiter().getId(), buchung.getStartZeit());

        return ResponseEntity.ok(buchungToMap(saved));
    }

    /**
     * Storniert eine Zeitbuchung (GoBD-konform: kein hartes Löschen).
     * Für manuelle Löschung: Buchung bleibt mit Storno-Audit markiert.
     */
    @DeleteMapping("/buchungen/{id}")
    public ResponseEntity<?> deleteBuchung(
            @PathVariable Long id,
            @RequestParam(required = false) Long bearbeiterId,
            @RequestParam(required = false) String grund) {

        if (grund == null || grund.isBlank()) {
            grund = "Manuelle Löschung im Büro";
        }

        Mitarbeiter bearbeiter = null;
        if (bearbeiterId != null) {
            bearbeiter = mitarbeiterRepository.findById(bearbeiterId).orElse(null);
        }
        if (bearbeiter == null) {
            bearbeiter = frontendUserProfileRepository.findAll().stream()
                    .filter(p -> p.getMitarbeiter() != null)
                    .map(p -> p.getMitarbeiter())
                    .findFirst()
                    .orElse(null);
        }

        Zeitbuchung buchung = zeitbuchungRepository.findById(id).orElse(null);
        Long mitarbeiterId = null;
        java.time.LocalDateTime startZeit = null;
        if (buchung != null) {
            mitarbeiterId = buchung.getMitarbeiter().getId();
            startZeit = buchung.getStartZeit();
            if (bearbeiter != null) {
                // WICHTIG: Erst Version erhöhen, dann Storno-Audit!
                buchung.markiereAlsGeaendert(bearbeiter);
                zeitbuchungRepository.save(buchung);

                // AUDIT: Stornierung mit NEUER Version protokollieren
                auditService.protokolliereStorno(buchung, bearbeiter, ErfassungsQuelle.DESKTOP, grund);
            }
        }

        zeitbuchungRepository.deleteById(id);

        // MonatsSaldo-Cache invalidieren
        if (mitarbeiterId != null && startZeit != null) {
            monatsSaldoService.invalidiereFuerDateTime(mitarbeiterId, startZeit);
        }

        return ResponseEntity.noContent().build();
    }

    // ==================== GoBD-Audit Endpoints ====================

    /**
     * Gibt die vollständige Änderungshistorie einer Zeitbuchung zurück.
     */
    @GetMapping("/buchungen/{id}/historie")
    public ResponseEntity<List<Map<String, Object>>> getBuchungHistorie(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getHistorie(id));
    }

    /**
     * Gibt alle verfügbaren Änderungsgründe für das Dropdown zurück.
     */
    @GetMapping("/aenderungsgruende")
    public ResponseEntity<List<Map<String, Object>>> getAenderungsgruende() {
        return ResponseEntity.ok(auditService.getAenderungsgruende());
    }

    /**
     * Erstellt eine neue Zeitbuchung.
     */
    @PostMapping("/buchungen")
    public ResponseEntity<Map<String, Object>> createBuchung(@RequestBody Map<String, Object> data) {
        Zeitbuchung buchung = new Zeitbuchung();

        // Typ früh setzen, um zu prüfen ob Projekt erforderlich ist
        BuchungsTyp typ = BuchungsTyp.ARBEIT; // Default
        if (data.containsKey("typ") && data.get("typ") != null) {
            String typStr = data.get("typ").toString();
            try {
                typ = BuchungsTyp.valueOf(typStr);
            } catch (IllegalArgumentException e) {
                typ = BuchungsTyp.ARBEIT;
            }
        }
        buchung.setTyp(typ);

        // Mitarbeiter setzen
        if (data.containsKey("mitarbeiterId")) {
            Long mitarbeiterId = Long.valueOf(data.get("mitarbeiterId").toString());
            Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                    .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));
            buchung.setMitarbeiter(mitarbeiter);
        }

        // Projekt setzen - nur für ARBEIT erforderlich, nicht für PAUSE
        if (data.containsKey("projektId") && data.get("projektId") != null) {
            Long projektId = Long.valueOf(data.get("projektId").toString());
            // -1 bedeutet "kein Projekt" (für Pausen)
            if (projektId > 0) {
                org.example.kalkulationsprogramm.domain.Projekt projekt = projektRepository.findById(projektId)
                        .orElseThrow(() -> new IllegalArgumentException("Projekt nicht gefunden: " + projektId));
                buchung.setProjekt(projekt);
            }
        } else if (typ != BuchungsTyp.PAUSE) {
            // Nur für ARBEIT ist projektId Pflicht
            throw new IllegalArgumentException("projektId ist erforderlich für Arbeitsbuchungen");
        }

        // Arbeitsgang/Stundensatz setzen
        if (data.containsKey("arbeitsgangId") && data.get("arbeitsgangId") != null) {
            Long arbeitsgangId = Long.valueOf(data.get("arbeitsgangId").toString());
            
            // Buchungsjahr aus Request-Daten extrahieren (buchung.getStartZeit() ist hier noch null!)
            int buchungsJahr = java.time.LocalDate.now().getYear(); // Default
            if (data.containsKey("startZeit") && data.get("startZeit") != null) {
                String startZeitStr = (String) data.get("startZeit");
                LocalDateTime parsedStart = LocalDateTime.parse(startZeitStr);
                buchungsJahr = parsedStart.getYear();
            }
            
            // Stundensatz für Buchungsjahr suchen, Fallback: nächstes verfügbares Jahr
            Optional<org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz> stundensatz = 
                    arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, buchungsJahr);
            if (stundensatz.isEmpty()) {
                // Fallback: Nächster verfügbarer Stundensatz (Jahr >= Buchungsjahr)
                stundensatz = arbeitsgangStundensatzRepository
                        .findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(arbeitsgangId, buchungsJahr);
            }
            if (stundensatz.isEmpty()) {
                // Letzter Fallback: Neuester Stundensatz überhaupt
                stundensatz = arbeitsgangStundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgangId);
            }
            stundensatz.ifPresent(buchung::setArbeitsgangStundensatz);
            
            // Direkten Arbeitsgang auch immer setzen
            arbeitsgangRepository.findById(arbeitsgangId)
                    .ifPresent(buchung::setArbeitsgang);
        }

        // ProjektProduktkategorie setzen (über Projekt-Kategorien)
        if (data.containsKey("produktkategorieId") && data.get("produktkategorieId") != null) {
            Long produktkategorieId = Long.valueOf(data.get("produktkategorieId").toString());
            org.example.kalkulationsprogramm.domain.Projekt p = buchung.getProjekt();
            if (p != null && p.getProjektProduktkategorien() != null) {
                p.getProjektProduktkategorien().stream()
                        .filter(pk -> pk.getProduktkategorie() != null &&
                                pk.getProduktkategorie().getId().equals(produktkategorieId))
                        .findFirst()
                        .ifPresent(buchung::setProjektProduktkategorie);
            }
        }

        // Zeiten setzen
        if (data.containsKey("startZeit")) {
            buchung.setStartZeit(LocalDateTime.parse((String) data.get("startZeit")));
        }
        if (data.containsKey("endeZeit") && data.get("endeZeit") != null) {
            buchung.setEndeZeit(LocalDateTime.parse((String) data.get("endeZeit")));
        }
        if (data.containsKey("notiz")) {
            buchung.setNotiz((String) data.get("notiz"));
        }

        // anzahlInStunden berechnen wenn Start- und Endezeit vorhanden
        if (buchung.getStartZeit() != null && buchung.getEndeZeit() != null) {
            java.time.Duration dauer = java.time.Duration.between(buchung.getStartZeit(), buchung.getEndeZeit());
            BigDecimal stunden = BigDecimal.valueOf(dauer.toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            buchung.setAnzahlInStunden(stunden);
        }

        Zeitbuchung saved = zeitbuchungRepository.save(buchung);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDateTime(
                saved.getMitarbeiter().getId(), saved.getStartZeit());

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(buchungToMap(saved));
    }

    // ==================== Kalender-Übersicht ====================

    /**
     * Gibt Kalenderdaten für einen Monat zurück (mit Buchungen, Feiertagen,
     * Sollstunden).
     */
    @GetMapping("/kalender")
    public ResponseEntity<Map<String, Object>> getKalender(
            @RequestParam Long mitarbeiterId,
            @RequestParam int jahr,
            @RequestParam int monat) {

        YearMonth yearMonth = YearMonth.of(jahr, monat);
        LocalDate ersterTag = yearMonth.atDay(1);
        LocalDate letzterTag = yearMonth.atEndOfMonth();

        // Feiertage laden
        List<Feiertag> feiertage = feiertagService.getFeiertageZwischen(ersterTag, letzterTag);
        Set<LocalDate> feiertagDaten = feiertage.stream()
                .map(Feiertag::getDatum)
                .collect(Collectors.toSet());

        // Zeitkonto laden
        Zeitkonto zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId);

        // Buchungen laden (Zeitbuchungen = Arbeitszeit)
        LocalDateTime startDateTime = ersterTag.atStartOfDay();
        List<Zeitbuchung> buchungen = zeitbuchungRepository
                .findByMitarbeiterIdAndStartZeitAfter(mitarbeiterId, startDateTime);

        // Abwesenheiten laden (Urlaub, Krankheit, Fortbildung)
        List<Abwesenheit> abwesenheiten = abwesenheitRepository
                .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag);

        // Buchungen nach Tag gruppieren
        Map<LocalDate, List<Map<String, Object>>> buchungenProTag = new HashMap<>();
        for (Zeitbuchung b : buchungen) {
            if (b.getStartZeit() != null &&
                    !b.getStartZeit().toLocalDate().isBefore(ersterTag) &&
                    !b.getStartZeit().toLocalDate().isAfter(letzterTag)) {

                LocalDate tag = b.getStartZeit().toLocalDate();
                buchungenProTag.computeIfAbsent(tag, k -> new ArrayList<>())
                        .add(buchungToMap(b));
            }
        }

        // Abwesenheiten als virtuelle Buchungen hinzufügen
        for (Abwesenheit a : abwesenheiten) {
            Map<String, Object> abwesenheitBuchung = new LinkedHashMap<>();
            abwesenheitBuchung.put("id", -a.getId()); // Negative ID um von echten Buchungen zu unterscheiden
            abwesenheitBuchung.put("projektId", null);
            abwesenheitBuchung.put("projektName", a.getTyp().name());
            abwesenheitBuchung.put("arbeitsgangId", null);
            abwesenheitBuchung.put("arbeitsgangName", "");
            abwesenheitBuchung.put("startZeit", "08:00:00");
            abwesenheitBuchung.put("endeZeit", null);
            abwesenheitBuchung.put("dauerMinuten", a.getStunden() != null
                    ? a.getStunden().multiply(new BigDecimal("60")).longValue()
                    : null);
            abwesenheitBuchung.put("dauerFormatiert", a.getStunden() != null
                    ? a.getStunden() + "h"
                    : null);
            abwesenheitBuchung.put("notiz", a.getNotiz());
            abwesenheitBuchung.put("typ", a.getTyp().name()); // URLAUB, KRANKHEIT, FORTBILDUNG

            buchungenProTag.computeIfAbsent(a.getDatum(), k -> new ArrayList<>())
                    .add(abwesenheitBuchung);
        }

        // Tage aufbauen
        List<Map<String, Object>> tage = new ArrayList<>();
        for (LocalDate tag = ersterTag; !tag.isAfter(letzterTag); tag = tag.plusDays(1)) {
            final LocalDate currentTag = tag; // effectively final für Lambda
            Map<String, Object> tagData = new LinkedHashMap<>();
            tagData.put("datum", currentTag.toString());
            tagData.put("wochentag", currentTag.getDayOfWeek().getValue());
            tagData.put("istFeiertag", feiertagDaten.contains(currentTag));
            tagData.put("feiertagName", feiertage.stream()
                    .filter(f -> f.getDatum().equals(currentTag))
                    .findFirst()
                    .map(Feiertag::getBezeichnung)
                    .orElse(null));
            tagData.put("sollStunden", feiertagDaten.contains(currentTag) ? BigDecimal.ZERO
                    : zeitkonto.getSollstundenFuerTag(currentTag.getDayOfWeek().getValue()));
            tagData.put("buchungen", buchungenProTag.getOrDefault(currentTag, Collections.emptyList()));

            // Ist-Stunden berechnen (inkl. Feiertage als Arbeitszeit)
            BigDecimal istStunden = BigDecimal.ZERO;

            // Feiertage: Wenn der Mitarbeiter an dem Wochentag normalerweise arbeiten
            // würde,
            // zählen die Sollstunden automatisch als Iststunden
            if (feiertagDaten.contains(currentTag)) {
                BigDecimal feiertagsStunden = zeitkonto.getSollstundenFuerTag(currentTag.getDayOfWeek().getValue());
                istStunden = istStunden.add(feiertagsStunden);
            }

            // Normale Buchungen dazuzählen (PAUSE ausschließen)
            for (Map<String, Object> buchung : buchungenProTag.getOrDefault(currentTag, Collections.emptyList())) {
                // PAUSE-Buchungen nicht zur Arbeitszeit zählen
                Object typObj = buchung.get("typ");
                if (typObj != null && "PAUSE".equals(typObj.toString())) {
                    continue;
                }
                if (buchung.get("dauerMinuten") != null) {
                    istStunden = istStunden.add(
                            new BigDecimal((Long) buchung.get("dauerMinuten"))
                                    .divide(new BigDecimal("60"), 2, java.math.RoundingMode.HALF_UP));
                }
            }
            tagData.put("istStunden", istStunden);

            tage.add(tagData);
        }

        // Monatssummen
        BigDecimal sollMonat = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, jahr, monat);
        BigDecimal istMonat = tage.stream()
                .map(t -> (BigDecimal) t.get("istStunden"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jahr", jahr);
        result.put("monat", monat);
        result.put("mitarbeiterId", mitarbeiterId);
        result.put("tage", tage);
        result.put("sollStundenMonat", sollMonat);
        result.put("istStundenMonat", istMonat);
        result.put("differenz", istMonat.subtract(sollMonat));
        result.put("feiertage", feiertage.stream()
                .map(f -> Map.of("datum", f.getDatum().toString(), "bezeichnung", f.getBezeichnung()))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }

    // ==================== Feiertage ====================

    @GetMapping("/feiertage")
    public ResponseEntity<List<Feiertag>> getFeiertage(@RequestParam int jahr) {
        return ResponseEntity.ok(feiertagService.getFeiertageForJahr(jahr));
    }

    /**
     * Gibt Feiertage in einem Datumsbereich zurück.
     * Für die mobile App: Prüfung vor Urlaubsantrag.
     */
    @GetMapping("/feiertage/zwischen")
    public ResponseEntity<List<Map<String, String>>> getFeiertageZwischen(
            @RequestParam String von,
            @RequestParam String bis) {
        LocalDate vonDatum = LocalDate.parse(von);
        LocalDate bisDatum = LocalDate.parse(bis);
        List<Feiertag> feiertage = feiertagService.getFeiertageZwischen(vonDatum, bisDatum);

        List<Map<String, String>> result = feiertage.stream()
                .map(f -> Map.of(
                        "datum", f.getDatum().toString(),
                        "bezeichnung", f.getBezeichnung()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/feiertage/regenerieren")
    public ResponseEntity<List<Feiertag>> regeneriereFeiertage(
            @RequestParam int vonJahr,
            @RequestParam int bisJahr) {
        return ResponseEntity.ok(feiertagService.regeneriereFeiertage(vonJahr, bisJahr));
    }

    // ==================== Zeitkonten ====================

    @GetMapping("/zeitkonten")
    public ResponseEntity<List<Map<String, Object>>> getAlleZeitkonten() {
        List<Mitarbeiter> mitarbeiter = mitarbeiterRepository.findAll();

        List<Map<String, Object>> result = mitarbeiter.stream()
                .map(m -> {
                    Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(m.getId());
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("mitarbeiterId", m.getId());
                    map.put("mitarbeiterName", m.getVorname() + " " + m.getNachname());
                    map.put("montagStunden", konto.getMontagStunden());
                    map.put("dienstagStunden", konto.getDienstagStunden());
                    map.put("mittwochStunden", konto.getMittwochStunden());
                    map.put("donnerstagStunden", konto.getDonnerstagStunden());
                    map.put("freitagStunden", konto.getFreitagStunden());
                    map.put("samstagStunden", konto.getSamstagStunden());
                    map.put("sonntagStunden", konto.getSonntagStunden());
                    map.put("wochenstunden", konto.getWochenstunden());
                    map.put("buchungStartZeit", konto.getBuchungStartZeit() != null ? konto.getBuchungStartZeit().toString() : null);
                    map.put("buchungEndeZeit", konto.getBuchungEndeZeit() != null ? konto.getBuchungEndeZeit().toString() : null);
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/zeitkonten/{mitarbeiterId}")
    public ResponseEntity<Zeitkonto> updateZeitkonto(
            @PathVariable Long mitarbeiterId,
            @RequestBody Map<String, Object> data) {

        BigDecimal montag = new BigDecimal(data.get("montagStunden").toString());
        BigDecimal dienstag = new BigDecimal(data.get("dienstagStunden").toString());
        BigDecimal mittwoch = new BigDecimal(data.get("mittwochStunden").toString());
        BigDecimal donnerstag = new BigDecimal(data.get("donnerstagStunden").toString());
        BigDecimal freitag = new BigDecimal(data.get("freitagStunden").toString());
        BigDecimal samstag = new BigDecimal(data.get("samstagStunden").toString());
        BigDecimal sonntag = new BigDecimal(data.get("sonntagStunden").toString());

        // Buchungszeitfenster parsen
        java.time.LocalTime buchungStart = data.get("buchungStartZeit") != null
                ? java.time.LocalTime.parse(data.get("buchungStartZeit").toString())
                : null;
        java.time.LocalTime buchungEnde = data.get("buchungEndeZeit") != null
                ? java.time.LocalTime.parse(data.get("buchungEndeZeit").toString())
                : null;

        Zeitkonto updated = zeitkontoService.aktualisiereZeitkonto(
                mitarbeiterId, montag, dienstag, mittwoch, donnerstag, freitag, samstag, sonntag);
        updated.setBuchungStartZeit(buchungStart);
        updated.setBuchungEndeZeit(buchungEnde);
        zeitkontoService.speichereZeitkonto(updated);

        // Sollstunden haben sich geändert → ALLE MonatsSaldo-Caches invalidieren
        monatsSaldoService.invalidiereAlle(mitarbeiterId);

        return ResponseEntity.ok(updated);
    }

    // ==================== Auswertungen ====================

    /**
     * Gibt eine Projektauswertung zurück (gruppiert nach
     * Tätigkeiten/Arbeitsgängen).
     */
    @GetMapping("/auswertung/projekt/{projektId}/pdf")
    public ResponseEntity<org.springframework.core.io.Resource> getProjektAuswertungPdf(
            @PathVariable Long projektId,
            @RequestParam(required = false) LocalDate von,
            @RequestParam(required = false) LocalDate bis) {

        java.nio.file.Path pdfPath = projektAuswertungPdfService.generatePdf(projektId, von, bis);

        try {
            org.springframework.core.io.UrlResource resource = new org.springframework.core.io.UrlResource(
                    pdfPath.toUri());
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"Regiebericht.pdf\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("Error loading PDF", e);
        }
    }

    @GetMapping("/auswertung/projekt/{projektId}")
    public ResponseEntity<Map<String, Object>> getProjektAuswertung(
            @PathVariable Long projektId,
            @RequestParam(required = false) LocalDate von,
            @RequestParam(required = false) LocalDate bis) {

        // Alle Buchungen für das Projekt aus ZeitbuchungRepository
        List<Zeitbuchung> alleBuchungen = zeitbuchungRepository.findByProjektId(projektId).stream()
                .filter(b -> {
                    if (von == null && bis == null)
                        return true;
                    LocalDate buchungsDatum = b.getStartZeit() != null ? b.getStartZeit().toLocalDate() : null;
                    if (buchungsDatum == null)
                        return false;
                    if (von != null && buchungsDatum.isBefore(von))
                        return false;
                    if (bis != null && buchungsDatum.isAfter(bis))
                        return false;
                    return true;
                })
                .collect(Collectors.toList());

        // Nach Arbeitsgang gruppieren
        Map<String, List<Zeitbuchung>> nachArbeitsgang = alleBuchungen.stream()
                .collect(Collectors.groupingBy(b -> b.getArbeitsgangStundensatz() != null
                        && b.getArbeitsgangStundensatz().getArbeitsgang() != null
                                ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                                : "Nicht zugeordnet"));

        List<Map<String, Object>> taetigkeiten = new ArrayList<>();
        BigDecimal gesamtStunden = BigDecimal.ZERO;

        for (Map.Entry<String, List<Zeitbuchung>> entry : nachArbeitsgang.entrySet()) {
            long gesamtMinuten = 0;
            List<Map<String, Object>> einzelBuchungen = new ArrayList<>();

            // Sort bookings by date/time
            List<Zeitbuchung> sortedBookings = new ArrayList<>(entry.getValue());
            sortedBookings.sort(Comparator.comparing(Zeitbuchung::getStartZeit));

            for (Zeitbuchung b : sortedBookings) {
                if (b.getStartZeit() != null && b.getEndeZeit() != null) {
                    gesamtMinuten += java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
                }
                einzelBuchungen.add(buchungToMap(b));
            }

            BigDecimal stunden = new BigDecimal(gesamtMinuten)
                    .divide(new BigDecimal("60"), 2, java.math.RoundingMode.HALF_UP);
            gesamtStunden = gesamtStunden.add(stunden);

            Map<String, Object> taetigkeit = new LinkedHashMap<>();
            taetigkeit.put("arbeitsgang", entry.getKey());
            taetigkeit.put("anzahlBuchungen", entry.getValue().size());
            taetigkeit.put("gesamtMinuten", gesamtMinuten);
            taetigkeit.put("gesamtStunden", stunden);
            taetigkeit.put("buchungen", einzelBuchungen);
            taetigkeiten.add(taetigkeit);
        }

        // Nach Stunden sortieren (absteigend)
        taetigkeiten.sort((a, b) -> ((BigDecimal) b.get("gesamtStunden"))
                .compareTo((BigDecimal) a.get("gesamtStunden")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projektId", projektId);
        if (!alleBuchungen.isEmpty()) {
            result.put("projektName", alleBuchungen.getFirst().getProjekt().getBauvorhaben());
            result.put("kunde", alleBuchungen.getFirst().getProjekt().getKunde());
            result.put("auftragsnummer", alleBuchungen.getFirst().getProjekt().getAuftragsnummer());
        }

        result.put("von", von);
        result.put("bis", bis);
        result.put("taetigkeiten", taetigkeiten);
        result.put("gesamtStunden", gesamtStunden);
        result.put("anzahlBuchungen", alleBuchungen.size());

        return ResponseEntity.ok(result);
    }

    // ==================== Hilfsmethoden ====================

    private Map<String, Object> buchungToMap(Zeitbuchung b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.getId());

        // Projekt kann für PAUSE-Buchungen null sein
        if (b.getProjekt() != null) {
            map.put("projektId", b.getProjekt().getId());
            map.put("projektName", b.getProjekt().getBauvorhaben());
        } else {
            map.put("projektId", null);
            map.put("projektName", b.getTyp() == BuchungsTyp.PAUSE ? "Pause" : null);
        }

        map.put("arbeitsgangId", b.getArbeitsgangStundensatz() != null &&
                b.getArbeitsgangStundensatz().getArbeitsgang() != null
                        ? b.getArbeitsgangStundensatz().getArbeitsgang().getId()
                        : (b.getArbeitsgang() != null ? b.getArbeitsgang().getId() : null));
        map.put("arbeitsgangName", b.getArbeitsgangStundensatz() != null &&
                b.getArbeitsgangStundensatz().getArbeitsgang() != null
                        ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                        : (b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : null));

        // Produktkategorie
        if (b.getProjektProduktkategorie() != null && b.getProjektProduktkategorie().getProduktkategorie() != null) {
            map.put("produktkategorieId", b.getProjektProduktkategorie().getProduktkategorie().getId());
            map.put("produktkategorieName", b.getProjektProduktkategorie().getProduktkategorie().getBezeichnung());
        } else {
            map.put("produktkategorieId", null);
            map.put("produktkategorieName", null);
        }

        // Mitarbeiter Name
        if (b.getMitarbeiter() != null) {
            map.put("mitarbeiterName", b.getMitarbeiter().getVorname() + " " + b.getMitarbeiter().getNachname());
        } else {
            map.put("mitarbeiterName", "Unbekannt");
        }

        // Return TIME ONLY (HH:mm) for frontend 'time' inputs
        map.put("startZeit",
                b.getStartZeit() != null ? b.getStartZeit().toLocalTime().toString().substring(0, 5) : null);
        map.put("endeZeit", b.getEndeZeit() != null ? b.getEndeZeit().toLocalTime().toString().substring(0, 5) : null);
        // Also return full datetime for reference
        map.put("startDateTime", b.getStartZeit() != null ? b.getStartZeit().toString() : null);
        map.put("endeDateTime", b.getEndeZeit() != null ? b.getEndeZeit().toString() : null);
        map.put("notiz", b.getNotiz());

        // Dauer berechnen
        if (b.getStartZeit() != null && b.getEndeZeit() != null) {
            long minuten = java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
            map.put("dauerMinuten", minuten);
            map.put("dauerFormatiert", "%d:%02d".formatted(minuten / 60, minuten % 60));
        } else {
            map.put("dauerMinuten", null);
            map.put("dauerFormatiert", null);
        }

        // Buchungstyp (ARBEIT oder PAUSE)
        map.put("typ", b.getTyp() != null ? b.getTyp().name() : "ARBEIT");

        return map;
    }

    // ==================== MonatsSaldo Cache ====================

    /**
     * Befüllt den MonatsSaldo-Cache für alle aktiven Mitarbeiter.
     * Kann manuell aufgerufen werden um den Cache nachträglich zu füllen.
     * GET /api/zeitverwaltung/saldo-cache/warmup
     */
    @PostMapping("/saldo-cache/warmup")
    public ResponseEntity<Map<String, Object>> warmupSaldoCache() {
        long start = System.currentTimeMillis();
        monatsSaldoWarmupService.warmupCache();
        long dauer = System.currentTimeMillis() - start;

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "MonatsSaldo-Cache wurde erfolgreich befüllt");
        result.put("dauerMs", dauer);
        return ResponseEntity.ok(result);
    }
}
