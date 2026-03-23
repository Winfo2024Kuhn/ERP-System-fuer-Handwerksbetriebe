package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service für die Zeiterfassungs-PWA.
 * Liefert gefilterte Daten und verarbeitet Zeitbuchungen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZeiterfassungApiService {

    private final ProjektRepository projektRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    private final ArbeitsgangMapper arbeitsgangMapper;
    private final DateiSpeicherService dateiSpeicherService;
    private final LieferantenRepository lieferantenRepository;
    private final FeiertagService feiertagService;
    private final ZeitbuchungAuditService auditService;

    // ==================== Daten abrufen ====================

    /**
     * Gibt nur offene Projekte zurück (abgeschlossen = false).
     * Zeigt alle Projekte, die nicht manuell als beendet markiert wurden.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOpenProjekte(Integer limit, String search) {
        List<Projekt> projekte;
        if (search != null && !search.isBlank()) {
            projekte = projektRepository.searchByBauvorhabenOrKundeOrEmail(search);
        } else {
            projekte = projektRepository.findAll();
        }

        return projekte.stream()
                .filter(p -> !p.isAbgeschlossen()) // Nur nicht-beendete Projekte
                .limit(limit != null && limit > 0 ? limit : Long.MAX_VALUE)
                .map(this::projektToSimpleMap)
                .collect(Collectors.toList());
    }

    /**
     * Gibt alle Produktkategorien mit vollem Pfad zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getKategorienMitPfad() {
        return produktkategorieRepository.findAll().stream()
                .map(this::kategorieToMapWithPath)
                .collect(Collectors.toList());
    }

    /**
     * Gibt nur die dem Projekt zugeordneten Produktkategorien zurück (via
     * ProjektProduktkategorie).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getKategorienByProjektId(Long projektId) {
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) {
            return Collections.emptyList();
        }

        return projekt.getProjektProduktkategorien().stream()
                .map(ProjektProduktkategorie::getProduktkategorie)
                .filter(Objects::nonNull)
                .map(this::kategorieToMapWithPath)
                .collect(Collectors.toList());
    }

    /**
     * Gibt Arbeitsgänge zurück, die zu den Abteilungen des Mitarbeiters gehören.
     * Bei N:M: Mitarbeiter kann mehreren Abteilungen angehören.
     */
    @Transactional(readOnly = true)
    public Optional<List<ArbeitsgangResponseDto>> getArbeitsgaengeByMitarbeiterToken(String token) {
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .map(mitarbeiter -> {
                    // Keine Abteilungen zugewiesen -> alle Arbeitsgänge zeigen
                    if (mitarbeiter.getAbteilungen() == null || mitarbeiter.getAbteilungen().isEmpty()) {
                        return arbeitsgangRepository.findAll().stream()
                                .sorted(Comparator.comparing(Arbeitsgang::getBeschreibung,
                                        String.CASE_INSENSITIVE_ORDER))
                                .map(arbeitsgangMapper::toArbeitsgangResponseDto)
                                .collect(Collectors.toList());
                    }

                    // IDs aller Abteilungen des Mitarbeiters sammeln
                    Set<Long> abteilungIds = mitarbeiter.getAbteilungen().stream()
                            .map(Abteilung::getId)
                            .collect(Collectors.toSet());

                    // Arbeitsgänge aus allen Abteilungen laden (alphabetisch sortiert)
                    return arbeitsgangRepository.findAll().stream()
                            .filter(ag -> ag.getAbteilung() != null &&
                                    abteilungIds.contains(ag.getAbteilung().getId()))
                            .sorted(Comparator.comparing(Arbeitsgang::getBeschreibung, String.CASE_INSENSITIVE_ORDER))
                            .map(arbeitsgangMapper::toArbeitsgangResponseDto)
                            .collect(Collectors.toList());
                });
    }

    /**
     * Gibt alle aktiven Lieferanten als einfache Liste zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLieferanten(Integer limit, String search) {
        List<Lieferanten> lieferanten;
        if (search != null && !search.isBlank()) {
            lieferanten = lieferantenRepository.searchByNameOrEmail(search);
        } else {
            lieferanten = lieferantenRepository.findByIstAktivTrueOrderByLieferantennameAsc();
        }

        return lieferanten.stream()
                .limit(limit != null && limit > 0 ? limit : Long.MAX_VALUE)
                .map(l -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", l.getId());
                    map.put("firmenname", l.getLieferantenname());

                    // Adressdaten
                    map.put("strasse", l.getStrasse());
                    map.put("plz", l.getPlz());
                    map.put("ort", l.getOrt());

                    // Kontaktdaten
                    map.put("telefon", l.getTelefon());
                    map.put("mobiltelefon", l.getMobiltelefon());
                    map.put("kundenEmails", l.getKundenEmails());

                    // Zusatzinfos
                    map.put("lieferantenTyp", l.getLieferantenTyp());
                    map.put("vertreter", l.getVertreter());
                    map.put("eigeneKundennummer", l.getEigeneKundennummer());

                    return map;
                })
                .collect(Collectors.toList());
    }

    // ==================== Zeiterfassung Start/Stop ====================

    /**
     * Startet eine neue Zeitbuchung für einen Mitarbeiter.
     * 
     * @param originalStartZeit Optionaler Original-Zeitstempel (für Offline-Sync).
     *                          Wenn null, wird LocalDateTime.now() verwendet.
     * @param idempotencyKey    Optionaler Idempotency-Key (UUID vom Client) zur
     *                          Vermeidung von Duplikaten bei Offline-Sync.
     */
    @Transactional
    public Map<String, Object> startZeiterfassung(String token, Long projektId, Long arbeitsgangId,
            Long produktkategorieId, LocalDateTime originalStartZeit, String idempotencyKey) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        // Idempotency-Check: Wenn dieser Key schon verarbeitet wurde, gib die
        // existierende Buchung zurück (statt 4xx). So kann der Client sicher
        // retries senden ohne Duplikate zu erzeugen.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Zeitbuchung> existing = zeitbuchungRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Zeitbuchung b = existing.get();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", b.getId());
                result.put("projektId", b.getProjekt() != null ? b.getProjekt().getId() : null);
                result.put("projektName", b.getProjekt() != null ? b.getProjekt().getBauvorhaben() : null);
                result.put("arbeitsgangId", b.getArbeitsgang() != null ? b.getArbeitsgang().getId() : null);
                result.put("arbeitsgangName",
                        b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : null);
                result.put("produktkategorieId", produktkategorieId);
                result.put("startZeit", b.getStartZeit().toString());
                result.put("status", "already_exists");
                result.put("idempotent", true);
                return result;
            }
        }

        // Prüfe ob bereits eine aktive Buchung existiert (in neuer Zeitbuchung-Tabelle)
        List<Zeitbuchung> aktiveBuchungen = zeitbuchungRepository
                .findByMitarbeiterIdAndEndeZeitIsNull(mitarbeiter.getId());

        if (!aktiveBuchungen.isEmpty()) {
            throw new RuntimeException("Es läuft bereits eine Buchung. Bitte erst stoppen.");
        }

        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden"));

        Arbeitsgang arbeitsgang = arbeitsgangRepository.findById(arbeitsgangId)
                .orElseThrow(() -> new RuntimeException("Arbeitsgang nicht gefunden"));

        Zeitbuchung buchung = new Zeitbuchung();
        buchung.setMitarbeiter(mitarbeiter);
        buchung.setProjekt(projekt);
        buchung.setArbeitsgang(arbeitsgang);
        buchung.setStartZeit(originalStartZeit != null ? originalStartZeit : LocalDateTime.now());

        // GoBD-konforme Audit-Felder setzen
        buchung.setErfasstVon(mitarbeiter);
        buchung.setErfasstAm(LocalDateTime.now());
        buchung.setErfasstVia(ErfassungsQuelle.MOBILE_APP);
        buchung.setVersion(1);

        // Idempotency-Key setzen (für Offline-Sync Deduplizierung)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            buchung.setIdempotencyKey(idempotencyKey);
        }

        // ProjektProduktkategorie setzen (falls ausgewählt)
        if (produktkategorieId != null) {
            projekt.getProjektProduktkategorien().stream()
                    .filter(pk -> pk.getProduktkategorie() != null &&
                            pk.getProduktkategorie().getId().equals(produktkategorieId))
                    .findFirst()
                    .ifPresent(buchung::setProjektProduktkategorie);
        }

        // Finde passenden Stundensatz für aktuelles Jahr oder neuesten verfügbaren
        int aktuellesJahr = LocalDateTime.now().getYear();
        ArbeitsgangStundensatz stundensatz = arbeitsgangStundensatzRepository
                .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, aktuellesJahr)
                .orElseGet(() -> arbeitsgangStundensatzRepository
                        .findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgangId)
                        .orElseThrow(() -> new RuntimeException(
                                "Kein Stundensatz für Arbeitsgang '" + arbeitsgang.getBeschreibung() + "' gefunden")));
        buchung.setArbeitsgangStundensatz(stundensatz);

        Zeitbuchung gespeichert = zeitbuchungRepository.save(buchung);

        // Audit-Protokoll: Erfassung loggen
        auditService.protokolliereErstellung(gespeichert, mitarbeiter, ErfassungsQuelle.MOBILE_APP);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDateTime(mitarbeiter.getId(), gespeichert.getStartZeit());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", gespeichert.getId());
        result.put("projektId", projektId);
        result.put("projektName", projekt.getBauvorhaben());
        result.put("arbeitsgangId", arbeitsgangId);
        result.put("arbeitsgangName", arbeitsgang.getBeschreibung());
        result.put("produktkategorieId", produktkategorieId);
        result.put("startZeit", gespeichert.getStartZeit().toString());
        result.put("status", "gestartet");

        return result;
    }

    /**
     * Stoppt die aktive Zeitbuchung für einen Mitarbeiter.
     * 
     * @param originalEndeZeit Optionaler Original-Zeitstempel (für Offline-Sync).
     *                         Wenn null, wird LocalDateTime.now() verwendet.
     */
    @Transactional
    public Map<String, Object> stopZeiterfassung(String token, LocalDateTime originalEndeZeit, String idempotencyKey) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Zeitbuchung> existing = zeitbuchungRepository.findByStopIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Zeitbuchung b = existing.get();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", b.getId());
                result.put("projektName", b.getProjekt() != null ? b.getProjekt().getBauvorhaben() : null);
                result.put("arbeitsgangName",
                        b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : null);
                result.put("startZeit", b.getStartZeit().toString());
                result.put("endeZeit", b.getEndeZeit() != null ? b.getEndeZeit().toString() : null);
                result.put("stunden", b.getAnzahlInStunden());
                result.put("typ", b.getTyp() != null ? b.getTyp().name() : "ARBEIT");
                result.put("status", "already_stopped");
                result.put("idempotent", true);
                return result;
            }
        }

        Zeitbuchung buchung = zeitbuchungRepository
                .findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(mitarbeiter.getId())
                .orElseThrow(() -> new RuntimeException("Keine aktive Buchung gefunden"));

        // Endezeit setzen
        LocalDateTime endeZeit = originalEndeZeit != null ? originalEndeZeit : LocalDateTime.now();

        // Sicherheitscheck: endeZeit muss nach startZeit liegen.
        // Kann durch Offline-Sync Race Conditions passieren (Stop-Entry mit altem Timestamp
        // trifft auf eine neue Buchung die erst nach dem Original-Stop gestartet wurde).
        if (!endeZeit.isAfter(buchung.getStartZeit())) {
            log.warn("endeZeit ({}) <= startZeit ({}) für Buchung {} (Mitarbeiter {}) - korrigiere auf startZeit + 1 Min",
                    endeZeit, buchung.getStartZeit(), buchung.getId(), mitarbeiter.getId());
            endeZeit = buchung.getStartZeit().plusMinutes(1);
        }

        buchung.setEndeZeit(endeZeit);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            buchung.setStopIdempotencyKey(idempotencyKey);
        }

        // Berechne Stunden
        Duration dauer = Duration.between(buchung.getStartZeit(), buchung.getEndeZeit());
        BigDecimal stunden = BigDecimal.valueOf(dauer.toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        buchung.setAnzahlInStunden(stunden);

        // WICHTIG: Erst Version erhöhen, dann Audit protokollieren!
        // Sonst gibt es einen Unique-Constraint-Fehler (Version 1 existiert bereits vom
        // Start)
        buchung.markiereAlsGeaendert(mitarbeiter);

        // Audit-Protokoll mit der NEUEN Version
        auditService.protokolliereAenderung(buchung, mitarbeiter, ErfassungsQuelle.MOBILE_APP,
                "Zeiterfassung beendet (Stop-Button am Handy)");

        zeitbuchungRepository.save(buchung);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDateTime(mitarbeiter.getId(), buchung.getStartZeit());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", buchung.getId());
        // Projekt kann bei PAUSE-Buchungen null sein
        result.put("projektName", buchung.getProjekt() != null ? buchung.getProjekt().getBauvorhaben() : null);
        result.put("arbeitsgangName",
                buchung.getArbeitsgang() != null ? buchung.getArbeitsgang().getBeschreibung() : null);
        result.put("startZeit", buchung.getStartZeit().toString());
        result.put("endeZeit", buchung.getEndeZeit().toString());
        result.put("stunden", stunden);
        result.put("typ", buchung.getTyp() != null ? buchung.getTyp().name() : "ARBEIT");
        result.put("status", "gestoppt");

        return result;
    }

    /**
     * Startet eine Pause: Stoppt die aktive Arbeitsbuchung und erstellt eine
     * PAUSE-Buchung.
     * Die Pausenbuchung wird als Zeitbuchung mit typ=PAUSE gespeichert.
     * PAUSE-Buchungen haben kein Projekt (projekt = null).
     * 
     * @param originalZeit Optionaler Original-Zeitstempel (für Offline-Sync).
     *                     Wenn null, wird LocalDateTime.now() verwendet.
     */
    @Transactional
    public Map<String, Object> startPause(String token, LocalDateTime originalZeit, String idempotencyKey) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Zeitbuchung> existing = zeitbuchungRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Zeitbuchung b = existing.get();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", b.getId());
                result.put("startZeit", b.getStartZeit().toString());
                result.put("typ", b.getTyp() != null ? b.getTyp().name() : "PAUSE");
                result.put("status", "already_exists");
                result.put("idempotent", true);
                return result;
            }
        }

        // Stoppe aktive Buchung (falls vorhanden)
        Optional<Zeitbuchung> aktiveBuchung = zeitbuchungRepository
                .findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(mitarbeiter.getId());

        if (aktiveBuchung.isPresent()) {
            Zeitbuchung buchung = aktiveBuchung.get();
            // Stoppe die aktive Arbeitsbuchung
            buchung.setEndeZeit(originalZeit != null ? originalZeit : LocalDateTime.now());
            Duration dauer = Duration.between(buchung.getStartZeit(), buchung.getEndeZeit());
            BigDecimal stunden = BigDecimal.valueOf(dauer.toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            buchung.setAnzahlInStunden(stunden);
            zeitbuchungRepository.save(buchung);
        }

        // Neue Pausenbuchung erstellen - OHNE Projekt
        Zeitbuchung pauseBuchung = new Zeitbuchung();
        pauseBuchung.setMitarbeiter(mitarbeiter);
        pauseBuchung.setProjekt(null); // Pausen haben kein Projekt
        pauseBuchung.setStartZeit(originalZeit != null ? originalZeit : LocalDateTime.now());
        pauseBuchung.setTyp(BuchungsTyp.PAUSE);
        pauseBuchung.setNotiz("Pausenbuchung");

        // GoBD-konforme Audit-Felder setzen
        pauseBuchung.setErfasstVon(mitarbeiter);
        pauseBuchung.setErfasstAm(LocalDateTime.now());
        pauseBuchung.setErfasstVia(ErfassungsQuelle.MOBILE_APP);
        pauseBuchung.setVersion(1);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            pauseBuchung.setIdempotencyKey(idempotencyKey);
        }

        Zeitbuchung gespeichert = zeitbuchungRepository.save(pauseBuchung);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", gespeichert.getId());
        result.put("startZeit", gespeichert.getStartZeit().toString());
        result.put("typ", "PAUSE");
        result.put("status", "gestartet");

        return result;
    }

    /**
     * Gibt die aktive Buchung für einen Mitarbeiter zurück (falls vorhanden).
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getAktiveBuchung(String token) {
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .flatMap(mitarbeiter -> zeitbuchungRepository
                        .findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(mitarbeiter.getId()))
                .map(buchung -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", buchung.getId());

                    // Projekt kann bei PAUSE-Buchungen null sein
                    if (buchung.getProjekt() != null) {
                        result.put("projektId", buchung.getProjekt().getId());
                        result.put("projektName", buchung.getProjekt().getBauvorhaben());
                        result.put("kundenName", buchung.getProjekt().getKunde());
                        result.put("auftragsnummer", buchung.getProjekt().getAuftragsnummer());
                    } else {
                        result.put("projektId", null);
                        result.put("projektName", buchung.getTyp() == BuchungsTyp.PAUSE ? "Pause" : null);
                        result.put("kundenName", null);
                        result.put("auftragsnummer", null);
                    }

                    // Arbeitsgang kann null sein
                    if (buchung.getArbeitsgang() != null) {
                        result.put("arbeitsgangId", buchung.getArbeitsgang().getId());
                        result.put("arbeitsgangName", buchung.getArbeitsgang().getBeschreibung());
                    } else {
                        result.put("arbeitsgangId", null);
                        result.put("arbeitsgangName", null);
                    }

                    // Produktkategorie kann null sein
                    if (buchung.getProjektProduktkategorie() != null
                            && buchung.getProjektProduktkategorie().getProduktkategorie() != null) {
                        Produktkategorie pk = buchung.getProjektProduktkategorie().getProduktkategorie();
                        result.put("produktkategorieId", pk.getId());
                        result.put("produktkategorieName", pk.getBezeichnung());
                    } else {
                        result.put("produktkategorieId", null);
                        result.put("produktkategorieName", null);
                    }

                    result.put("typ", buchung.getTyp() != null ? buchung.getTyp().name() : null);
                    result.put("startZeit", buchung.getStartZeit().toString());
                    return result;
                });
    }

    /**
     * Gibt die heute gearbeiteten Stunden für einen Mitarbeiter zurück.
     * Zählt nur tatsächliche Arbeitszeit aus der neuen Zeitbuchung-Tabelle.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getHeuteGearbeitet(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stunden", 0);
        result.put("minuten", 0);
        result.put("buchungenAnzahl", 0);

        Optional<Mitarbeiter> mitarbeiterOpt = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token);
        if (mitarbeiterOpt.isEmpty()) {
            return result;
        }

        Mitarbeiter mitarbeiter = mitarbeiterOpt.get();
        LocalDateTime heuteMitternacht = LocalDateTime.now().toLocalDate().atStartOfDay();

        List<Zeitbuchung> heutigeBuchungen = zeitbuchungRepository
                .findByMitarbeiterIdAndStartZeitAfter(mitarbeiter.getId(), heuteMitternacht);

        long totalMinuten = 0;
        int anzahlArbeitsBuchungen = 0;

        for (Zeitbuchung buchung : heutigeBuchungen) {
            // PAUSE-Buchungen nicht zur Arbeitszeit zählen
            if (buchung.getTyp() == BuchungsTyp.PAUSE) {
                continue;
            }
            anzahlArbeitsBuchungen++;

            if (buchung.getAnzahlInStunden() != null) {
                // Abgeschlossene Buchung -> anzahlInStunden nutzen
                totalMinuten += buchung.getAnzahlInStunden()
                        .multiply(BigDecimal.valueOf(60))
                        .longValue();
            } else if (buchung.getEndeZeit() == null && buchung.getStartZeit() != null) {
                // Aktive Buchung -> bis jetzt berechnen
                Duration dauer = Duration.between(buchung.getStartZeit(), LocalDateTime.now());
                totalMinuten += dauer.toMinutes();
            }
        }

        result.put("stunden", (int) (totalMinuten / 60));
        result.put("minuten", (int) (totalMinuten % 60));
        result.put("buchungenAnzahl", anzahlArbeitsBuchungen);

        return result;
    }

    /**
     * Gibt alle Buchungen für einen Mitarbeiter an einem bestimmten Datum zurück.
     * Zeiten sind in Minuten ab Mitternacht.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuchungenByDatum(String token, LocalDate datum) {
        Optional<Mitarbeiter> mitarbeiterOpt = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token);
        if (mitarbeiterOpt.isEmpty()) {
            return List.of();
        }
        Mitarbeiter mitarbeiter = mitarbeiterOpt.get();

        LocalDateTime startOfDay = datum.atStartOfDay();
        LocalDateTime endOfDay = datum.plusDays(1).atStartOfDay();

        // Alle Buchungen des Tages aus Zeitbuchung-Tabelle
        List<Zeitbuchung> buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                mitarbeiter.getId(), startOfDay, endOfDay);

        return buchungen.stream()
                .map(b -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", b.getId());

                    // Start/Ende in Minuten ab Mitternacht
                    if (b.getStartZeit() != null) {
                        entry.put("startMinuten", b.getStartZeit().getHour() * 60 + b.getStartZeit().getMinute());
                    }
                    if (b.getEndeZeit() != null) {
                        entry.put("endeMinuten", b.getEndeZeit().getHour() * 60 + b.getEndeZeit().getMinute());
                    }

                    // Dauer in Minuten
                    BigDecimal stunden = b.getAnzahlInStunden();
                    if (stunden != null) {
                        entry.put("dauerMinuten", stunden.multiply(BigDecimal.valueOf(60)).intValue());
                    } else if (b.getStartZeit() != null && b.getEndeZeit() != null) {
                        long minutes = java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
                        entry.put("dauerMinuten", (int) minutes);
                    }

                    // Projekt (kann bei PAUSE null sein)
                    if (b.getProjekt() != null) {
                        entry.put("projektId", b.getProjekt().getId());
                        entry.put("projektNummer", b.getProjekt().getAuftragsnummer());
                        entry.put("projektName", b.getProjekt().getBauvorhaben());
                        entry.put("kundenName", b.getProjekt().getKunde());
                    } else {
                        entry.put("projektId", null);
                        entry.put("projektNummer", null);
                        entry.put("projektName", b.getTyp() == BuchungsTyp.PAUSE ? "Pause" : null);
                        entry.put("kundenName", null);
                    }

                    // Arbeitsgang / Tätigkeit
                    if (b.getArbeitsgang() != null) {
                        entry.put("arbeitsgangId", b.getArbeitsgang().getId());
                        entry.put("taetigkeit", b.getArbeitsgang().getBeschreibung());
                    }

                    // Produktkategorie
                    if (b.getProjektProduktkategorie() != null
                            && b.getProjektProduktkategorie().getProduktkategorie() != null) {
                        Produktkategorie pk = b.getProjektProduktkategorie().getProduktkategorie();
                        entry.put("kategorieId", pk.getId());
                        entry.put("kategorieName", buildFullPath(pk));
                    }

                    // Notiz/Kommentar
                    entry.put("kommentar", b.getNotiz());

                    // Buchungstyp (ARBEIT oder PAUSE)
                    entry.put("typ", b.getTyp() != null ? b.getTyp().name() : "ARBEIT");

                    return entry;
                })
                .collect(Collectors.toList());
    }

    // ==================== Hilfsmethoden ====================

    private Map<String, Object> projektToSimpleMap(Projekt p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getBauvorhaben());
        map.put("projektNummer", p.getAuftragsnummer());
        map.put("kundenName", p.getKunde());

        // Projektart (PAUSCHAL, REGIE, INTERN, GARANTIE)
        map.put("projektArt", p.getProjektArt().name());
        map.put("projektArtDisplayName", p.getProjektArt().getDisplayName());

        // Projekt-Status für 3-Phasen-Anzeige (Offen, Bezahlt, Beendet)
        map.put("bezahlt", p.isBezahlt());
        map.put("abgeschlossen", p.isAbgeschlossen());

        // Projektadresse (Bauvorhaben-Adresse vom Projekt-Entity)
        map.put("projektStrasse", p.getStrasse());
        map.put("projektPlz", p.getPlz());
        map.put("projektOrt", p.getOrt());

        // Kundenadresse (Fallback wenn keine Projektadresse)
        if (p.getKundenId() != null) {
            map.put("kundenStrasse", p.getKundenId().getStrasse());
            map.put("kundenPlz", p.getKundenId().getPlz());
            map.put("kundenOrt", p.getKundenId().getOrt());
            map.put("kundenTelefon", p.getKundenId().getTelefon());
            map.put("kundenMobil", p.getKundenId().getMobiltelefon());
            map.put("ansprechpartner", p.getKundenId().getAnsprechspartner());
            map.put("kundennummer", p.getKundenId().getKundennummer());
        } else {
            // Fallback: Projektadresse auch als Kundenadresse (für Kompatibilität)
            map.put("kundenStrasse", p.getStrasse());
            map.put("kundenPlz", p.getPlz());
            map.put("kundenOrt", p.getOrt());
        }
        return map;
    }

    private Map<String, Object> kategorieToMapWithPath(Produktkategorie k) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", k.getId());
        map.put("name", buildFullPath(k));
        map.put("bezeichnung", k.getBezeichnung());
        return map;
    }

    /**
     * Baut den vollen Pfad der Kategorie rekursiv auf (z.B. "Dach / Dachdeckung /
     * Flachdach").
     */
    private String buildFullPath(Produktkategorie k) {
        List<String> pathParts = new ArrayList<>();
        Produktkategorie current = k;

        while (current != null) {
            pathParts.add(0, current.getBezeichnung());
            current = current.getUebergeordneteKategorie();
        }

        return String.join(" / ", pathParts);
    }

    /**
     * Gibt alle Bilder (DokumentGruppe=BILDER) für ein Projekt zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProjektBilder(Long projektId) {
        List<ProjektDokument> alleDokumente = dateiSpeicherService.holeDokumenteZuProjekt(projektId);

        return alleDokumente.stream()
                .filter(dok -> dok.getDokumentGruppe() == DokumentGruppe.BILDER)
                .map(dok -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", dok.getId());
                    map.put("name", dok.getOriginalDateiname());
                    // Der tatsächliche Endpoint ist /api/dokumente/{gespeicherterDateiname}
                    map.put("url", "/api/dokumente/" + dok.getGespeicherterDateiname());
                    map.put("thumbnailUrl", "/api/dokumente/" + dok.getGespeicherterDateiname() + "/thumbnail");
                    map.put("uploadDatum", dok.getUploadDatum());
                    if (dok.getUploadedBy() != null) {
                        map.put("uploadedByVorname", dok.getUploadedBy().getVorname());
                        map.put("uploadedByNachname", dok.getUploadedBy().getNachname());
                    }
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gibt alle Feiertage für ein Jahr zurück (Bayern).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFeiertage(int jahr) {
        return feiertagService.getFeiertageForJahr(jahr).stream()
                .map(f -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("datum", f.getDatum().toString());
                    map.put("bezeichnung", f.getBezeichnung());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // ==================== Saldenauswertung ====================

    /**
     * Berechnet die Saldenauswertung für einen Mitarbeiter:
     * - Urlaubstage: genommen und verbleibend im Jahr (aus Abwesenheit-Tabelle)
     * - Monatsstunden: Soll, Ist, Differenz (aus Zeitbuchung-Tabelle)
     * - Gesamtsaldo: +/- Stunden insgesamt
     * 
     * @param gesamtBisHeute wenn true, wird Gesamtsaldo immer bis heute berechnet
     *                       (für Mobile App)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSaldo(String token, Integer jahr, Integer monat, Boolean gesamtBisHeute) {
        Map<String, Object> result = new LinkedHashMap<>();

        Optional<Mitarbeiter> mitarbeiterOpt = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token);
        if (mitarbeiterOpt.isEmpty()) {
            result.put("error", "Mitarbeiter nicht gefunden");
            return result;
        }

        Mitarbeiter mitarbeiter = mitarbeiterOpt.get();
        // Use provided year/month or default to current
        int currentYear = (jahr != null) ? jahr : java.time.LocalDate.now().getYear();
        int currentMonth = (monat != null) ? monat : java.time.LocalDate.now().getMonthValue();

        // ========== 1. URLAUB (aus Abwesenheit-Tabelle) ==========
        Integer jahresUrlaub = mitarbeiter.getJahresUrlaub();
        if (jahresUrlaub == null)
            jahresUrlaub = 30; // Default: 30 Tage

        // Zähle Abwesenheiten aus der Abwesenheit-Tabelle
        java.time.LocalDate heute = java.time.LocalDate.now();
        java.time.LocalDate jahresanfang = java.time.LocalDate.of(currentYear, 1, 1);
        java.time.LocalDate jahresende = java.time.LocalDate.of(currentYear, 12, 31);

        List<Abwesenheit> abwesenheitenImJahr = abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(
                mitarbeiter.getId(), jahresanfang, jahresende);

        long urlaubstagGenommen = abwesenheitenImJahr.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB)
                .filter(a -> a.getDatum().isBefore(heute))
                .count();

        long urlaubstagGeplant = abwesenheitenImJahr.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB)
                .filter(a -> !a.getDatum().isBefore(heute))
                .count();

        long krankheitsTage = abwesenheitenImJahr.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.KRANKHEIT)
                .count();

        long fortbildungsTage = abwesenheitenImJahr.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.FORTBILDUNG)
                .count();

        // Manuelle Urlaubskorrekturen abrufen (als Tage, gespeichert im Feld 'stunden')
        BigDecimal manuellKorrekturBD = zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(
                mitarbeiter.getId(), currentYear);
        int manuellKorrekturTage = manuellKorrekturBD != null ? manuellKorrekturBD.intValue() : 0;

        // verbleibend = Anspruch - Genommen - Geplant + Korrekturen
        int urlaubstagVerbleibend = (int) (jahresUrlaub - urlaubstagGenommen - urlaubstagGeplant
                + manuellKorrekturTage);

        Map<String, Object> urlaub = new LinkedHashMap<>();
        urlaub.put("jahresanspruch", jahresUrlaub);
        urlaub.put("genommen", urlaubstagGenommen);
        urlaub.put("geplant", urlaubstagGeplant);
        urlaub.put("korrektur", manuellKorrekturTage);
        urlaub.put("verbleibend", Math.max(0, urlaubstagVerbleibend));
        urlaub.put("krankheitsTage", krankheitsTage);
        urlaub.put("fortbildungsTage", fortbildungsTage);
        result.put("urlaub", urlaub);

        // ========== 2. AKTUELLER MONAT (via MonatsSaldo-Cache) ==========
        MonatsSaldo monatsSaldo = monatsSaldoService.getOrBerechne(mitarbeiter.getId(), currentYear, currentMonth);

        BigDecimal sollStundenMonat = monatsSaldo.getSollStunden();
        BigDecimal monatsDifferenz = monatsSaldo.getGesamtIst().subtract(sollStundenMonat);

        Map<String, Object> monatData = new LinkedHashMap<>();
        monatData.put("name", java.time.Month.of(currentMonth).getDisplayName(java.time.format.TextStyle.FULL,
                java.util.Locale.GERMAN));
        monatData.put("monatNummer", currentMonth);
        monatData.put("sollStunden", sollStundenMonat);
        monatData.put("istStunden", monatsSaldo.getGesamtIst());
        monatData.put("differenz", monatsDifferenz);
        result.put("monat", monatData);

        // ========== 3. GESAMTSALDO (via monatliche Zwischenspeicherung) ==========
        // Startdatum bestimmen: Eintrittsdatum oder erste Buchung
        java.time.LocalDate startDatum = mitarbeiter.getEintrittsdatum();

        // Wenn kein Eintrittsdatum, erste Zeitbuchung suchen
        if (startDatum == null) {
            Optional<Zeitbuchung> ersteBuchung = zeitbuchungRepository
                    .findFirstByMitarbeiterIdOrderByStartZeitAsc(mitarbeiter.getId());
            if (ersteBuchung.isPresent()) {
                startDatum = ersteBuchung.get().getStartZeit().toLocalDate();
            } else {
                // Fallback: Jahresanfang des aktuellen Jahres (wenn gar nichts da ist)
                startDatum = java.time.LocalDate.of(currentYear, 1, 1);
            }
        }

        // ========== ENDDATUM für GESAMTSALDO bestimmen ==========
        java.time.LocalDate endDatum;
        int tatsaechlichesJahr = java.time.LocalDate.now().getYear();

        if (Boolean.TRUE.equals(gesamtBisHeute)) {
            endDatum = heute;
        } else if (currentYear == tatsaechlichesJahr) {
            endDatum = heute;
        } else {
            endDatum = java.time.LocalDate.of(currentYear, 12, 31);
        }

        // Gesamtsaldo via monatliche Zwischenspeicherung berechnen
        // Iteriert über jeden Monat von startDatum bis endDatum und nutzt den Cache
        BigDecimal gesamtIst = BigDecimal.ZERO;
        BigDecimal gesamtSoll = BigDecimal.ZERO;

        java.time.YearMonth startYM = java.time.YearMonth.from(startDatum);
        java.time.YearMonth endYM = java.time.YearMonth.from(endDatum);

        for (java.time.YearMonth ym = startYM; !ym.isAfter(endYM); ym = ym.plusMonths(1)) {
            MonatsSaldo ms = monatsSaldoService.getOrBerechne(
                    mitarbeiter.getId(), ym.getYear(), ym.getMonthValue());

            // Für den ersten Monat: Nur ab startDatum rechnen (anteilig)
            // Für den letzten Monat: Nur bis endDatum rechnen (anteilig)
            // Für vollständige Monate: Kompletten Cache nehmen
            boolean istErsterMonat = ym.equals(startYM) && startDatum.getDayOfMonth() > 1;
            boolean istLetzterMonat = ym.equals(endYM) && endDatum.getDayOfMonth() < ym.lengthOfMonth();

            if (istErsterMonat || istLetzterMonat) {
                // Anteilig: Für Rand-Monate direkt aus Quelldaten berechnen
                // (nur der erste und letzte Monat können anteilig sein)
                java.time.LocalDate monatVon = istErsterMonat ? startDatum : ym.atDay(1);
                java.time.LocalDate monatBis = istLetzterMonat ? endDatum : ym.atEndOfMonth();

                gesamtIst = gesamtIst.add(berechneAnteiligenMonatIst(
                        mitarbeiter.getId(), monatVon, monatBis));
                gesamtSoll = gesamtSoll.add(zeitkontoService.berechneSollstundenFuerZeitraum(
                        zeitkontoService.getOrCreateZeitkonto(mitarbeiter.getId()), monatVon, monatBis));
            } else {
                // Vollständiger Monat: Aus Cache nehmen
                gesamtIst = gesamtIst.add(ms.getGesamtIst());
                gesamtSoll = gesamtSoll.add(ms.getSollStunden());
            }
        }

        BigDecimal gesamtSaldo = gesamtIst.subtract(gesamtSoll);

        Map<String, Object> gesamt = new LinkedHashMap<>();
        gesamt.put("istStunden", gesamtIst);
        gesamt.put("sollStunden", gesamtSoll);
        gesamt.put("saldo", gesamtSaldo);
        gesamt.put("startDatum", startDatum.toString());
        gesamt.put("endDatum", endDatum.toString());
        result.put("gesamt", gesamt);

        result.put("mitarbeiterName", mitarbeiter.getVorname() + " " + mitarbeiter.getNachname());
        result.put("jahr", currentYear);

        return result;
    }

    /**
     * Gibt eine Urlaubsverfall-Warnung zurück, falls Resturlaub bald verfällt.
     * Warnung erscheint ab 1. Dezember bis 31. Januar.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUrlaubsverfallWarnung(String token) {
        return urlaubsverfallService.pruefeVerfallWarnungByToken(token)
                .orElse(Collections.emptyMap());
    }

    /**
     * Berechnet die Stunden für bezahlte Feiertage in einem Zeitraum.
     * Nur Feiertage an Arbeitstagen (Mo-Fr) werden gezählt.
     * Halbe Feiertage (z.B. Heiligabend) zählen 50%.
     */
    /**
     * Berechnet die Stunden für bezahlte Feiertage in einem Zeitraum.
     * Nur Feiertage an Arbeitstagen (Mo-Fr) werden gezählt.
     * Halbe Feiertage (z.B. Heiligabend) zählen 50%.
     */
    private BigDecimal berechneFeiertagsStunden(Zeitkonto zeitkonto, java.time.LocalDate von, java.time.LocalDate bis) {
        BigDecimal summe = BigDecimal.ZERO;

        for (java.time.LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            int wochentag = tag.getDayOfWeek().getValue();
            BigDecimal tagesSoll = zeitkonto.getSollstundenFuerTag(wochentag);

            // Nur Feiertage an Arbeitstagen zählen (nicht an Wochenenden)
            if (tagesSoll.compareTo(BigDecimal.ZERO) > 0 && feiertagService.istFeiertag(tag)) {
                if (feiertagService.istHalberFeiertag(tag)) {
                    // Halber Feiertag: 50% der Sollstunden
                    summe = summe.add(tagesSoll.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP));
                } else {
                    // Voller Feiertag: volle Sollstunden
                    summe = summe.add(tagesSoll);
                }
            }
        }

        return summe;
    }

    /**
     * Berechnet die anteiligen Ist-Stunden für einen Teilzeitraum innerhalb eines Monats.
     * Wird für den ersten und letzten Monat des Gesamtsaldo-Bereichs verwendet,
     * wenn das Start-/Enddatum nicht auf den Monatsersten/-letzten fällt.
     * 
     * Berücksichtigt: Zeitbuchungen + Abwesenheiten + Feiertage + Korrekturen.
     */
    private BigDecimal berechneAnteiligenMonatIst(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        LocalDateTime vonDT = von.atStartOfDay();
        LocalDateTime bisDT = bis.atTime(23, 59, 59);

        // Zeitbuchungsstunden (ohne Pausen)
        BigDecimal istStunden = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                mitarbeiterId, vonDT, bisDT).stream()
                .filter(b -> b.getTyp() != BuchungsTyp.PAUSE)
                .filter(b -> b.getAnzahlInStunden() != null)
                .map(Zeitbuchung::getAnzahlInStunden)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Abwesenheitsstunden
        BigDecimal abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                mitarbeiterId, von, bis);
        if (abwesenheitsStunden == null) abwesenheitsStunden = BigDecimal.ZERO;

        // Feiertagsstunden
        Zeitkonto zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId);
        BigDecimal feiertagsStunden = berechneFeiertagsStunden(zeitkonto, von, bis);

        // Korrekturstunden im Teilzeitraum
        BigDecimal korrekturStunden = zeitkontoKorrekturService.summiereAktiveKorrekturenImZeitraum(
                mitarbeiterId, von, bis);

        return istStunden.add(abwesenheitsStunden).add(feiertagsStunden).add(korrekturStunden);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ZeitkontoService zeitkontoService;

    @org.springframework.beans.factory.annotation.Autowired
    private UrlaubsverfallService urlaubsverfallService;

    @org.springframework.beans.factory.annotation.Autowired
    private ZeitkontoKorrekturService zeitkontoKorrekturService;

    @org.springframework.beans.factory.annotation.Autowired
    private MonatsSaldoService monatsSaldoService;

    /**
     * Gibt das erlaubte Buchungszeitfenster für einen Mitarbeiter zurück.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBuchungszeitfenster(String token) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(mitarbeiter.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buchungStartZeit", konto.getBuchungStartZeit() != null ? konto.getBuchungStartZeit().toString() : null);
        result.put("buchungEndeZeit", konto.getBuchungEndeZeit() != null ? konto.getBuchungEndeZeit().toString() : null);
        return result;
    }
}
