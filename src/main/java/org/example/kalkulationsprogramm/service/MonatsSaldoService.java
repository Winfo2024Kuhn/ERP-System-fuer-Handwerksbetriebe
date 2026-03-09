package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Service für die monatliche Saldo-Zwischenspeicherung.
 * 
 * Caching-Strategie:
 * - Abgeschlossene Monate werden berechnet und in der DB zwischengespeichert.
 * - Bei Datenänderungen (Buchungen, Abwesenheiten, Korrekturen) wird der
 *   betroffene Monat invalidiert und bei der nächsten Abfrage neu berechnet.
 * - Der aktuelle (laufende) Monat wird IMMER live berechnet, nie gecached.
 * 
 * Rechtliche Sicherheit:
 * - Der Cache ist ausschließlich ein Performance-Mechanismus.
 * - Alle Quelldaten (Zeitbuchung, Abwesenheit, ZeitkontoKorrektur) bleiben
 *   unverändert mit ihrem vollständigen Audit-Trail bestehen.
 * - Bei Invalidierung wird sofort sauber aus den Quelldaten neu berechnet.
 */
@Service
@RequiredArgsConstructor
public class MonatsSaldoService {

    private static final Logger log = LoggerFactory.getLogger(MonatsSaldoService.class);

    private final MonatsSaldoRepository monatsSaldoRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final ZeitkontoKorrekturRepository korrekturRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitkontoService zeitkontoService;
    private final FeiertagService feiertagService;

    @Autowired
    @Lazy
    private MonatsSaldoService self;

    // ==================== Cache-Abfrage ====================

    /**
     * Gibt den MonatsSaldo für einen Monat zurück.
     * Falls der Cache gültig ist, wird er direkt geliefert.
     * Falls nicht, wird er neu berechnet und gespeichert.
     * 
     * Der aktuelle Monat wird NIE gecached, sondern immer live berechnet.
     * 
     * @return MonatsSaldo mit allen Komponenten
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonatsSaldo getOrBerechne(Long mitarbeiterId, int jahr, int monat) {
        // Aktueller Monat: immer live berechnen
        LocalDate heute = LocalDate.now();
        if (jahr == heute.getYear() && monat == heute.getMonthValue()) {
            return berechneMonatsSaldo(mitarbeiterId, jahr, monat);
        }

        // Zukünftige Monate: ebenfalls immer live berechnen
        YearMonth abfrage = YearMonth.of(jahr, monat);
        YearMonth aktuell = YearMonth.of(heute.getYear(), heute.getMonthValue());
        if (abfrage.isAfter(aktuell)) {
            return berechneMonatsSaldo(mitarbeiterId, jahr, monat);
        }

        // Vergangener Monat: Cache prüfen
        Optional<MonatsSaldo> cached = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(
                mitarbeiterId, jahr, monat);

        if (cached.isPresent() && Boolean.TRUE.equals(cached.get().getGueltig())) {
            return cached.get();
        }

        // Cache ungültig oder nicht vorhanden → neu berechnen
        MonatsSaldo berechnet = berechneMonatsSaldo(mitarbeiterId, jahr, monat);

        // Speichern in separater TX, damit ein Duplicate-Key-Fehler
        // diese TX nicht vergiftet
        try {
            return self.saveMonatsSaldoCache(mitarbeiterId, jahr, monat, berechnet);
        } catch (DataIntegrityViolationException e) {
            log.debug("MonatsSaldo-Cache concurrent insert für MA={}, {}/{} – verwende berechneten Wert",
                    mitarbeiterId, jahr, monat);
            return berechnet;
        }
    }

    // ==================== Berechnung ====================

    /**
     * Berechnet den MonatsSaldo aus den Quelldaten (Zeitbuchungen, Abwesenheiten, etc.).
     * Gibt ein transientes (nicht gespeichertes) MonatsSaldo-Objekt zurück.
     */
    private MonatsSaldo berechneMonatsSaldo(Long mitarbeiterId, int jahr, int monat) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();
        LocalDateTime startDT = ersterTag.atStartOfDay();
        LocalDateTime endDT = letzterTag.atTime(23, 59, 59);

        // 1. Ist-Stunden aus Zeitbuchungen (ohne PAUSE)
        List<Zeitbuchung> buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                mitarbeiterId, startDT, endDT);
        BigDecimal istStunden = buchungen.stream()
                .filter(b -> b.getTyp() != BuchungsTyp.PAUSE)
                .filter(b -> b.getAnzahlInStunden() != null)
                .map(Zeitbuchung::getAnzahlInStunden)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Soll-Stunden aus Zeitkonto
        BigDecimal sollStunden = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, jahr, monat);

        // 3. Abwesenheitsstunden
        BigDecimal abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                mitarbeiterId, ersterTag, letzterTag);
        if (abwesenheitsStunden == null) abwesenheitsStunden = BigDecimal.ZERO;

        // 4. Feiertagsstunden
        Zeitkonto zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId);
        BigDecimal feiertagsStunden = berechneFeiertagsStunden(zeitkonto, ersterTag, letzterTag);

        // 5. Korrekturstunden (nur STUNDEN-Typ, nicht storniert, Datum im Monat)
        BigDecimal korrekturStunden = korrekturRepository
                .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag)
                .stream()
                .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                .filter(k -> k.getTyp() == KorrekturTyp.STUNDEN)
                .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // MonatsSaldo zusammenbauen (transient)
        MonatsSaldo saldo = new MonatsSaldo();
        saldo.setJahr(jahr);
        saldo.setMonat(monat);
        saldo.setIstStunden(istStunden);
        saldo.setSollStunden(sollStunden);
        saldo.setAbwesenheitsStunden(abwesenheitsStunden);
        saldo.setFeiertagsStunden(feiertagsStunden);
        saldo.setKorrekturStunden(korrekturStunden);
        saldo.setGueltig(true);
        saldo.setBerechnetAm(LocalDateTime.now());

        return saldo;
    }

    /**
     * Speichert einen berechneten MonatsSaldo in die DB (Insert oder Update).
     * Läuft in einer eigenen REQUIRES_NEW-Transaktion, damit ein
     * Duplicate-Key-Fehler (Race Condition bei Concurrent Warmup + API)
     * die aufrufende Transaktion nicht vergiftet.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonatsSaldo saveMonatsSaldoCache(Long mitarbeiterId, int jahr, int monat,
                                            MonatsSaldo berechnet) {
        // In dieser neuen TX nochmals prüfen – ein anderer Thread könnte
        // zwischenzeitlich committed haben
        Optional<MonatsSaldo> existing = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(
                mitarbeiterId, jahr, monat);

        MonatsSaldo entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            entity = new MonatsSaldo();
            Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                    .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));
            entity.setMitarbeiter(mitarbeiter);
            entity.setJahr(jahr);
            entity.setMonat(monat);
        }

        entity.setIstStunden(berechnet.getIstStunden());
        entity.setSollStunden(berechnet.getSollStunden());
        entity.setAbwesenheitsStunden(berechnet.getAbwesenheitsStunden());
        entity.setFeiertagsStunden(berechnet.getFeiertagsStunden());
        entity.setKorrekturStunden(berechnet.getKorrekturStunden());
        entity.setGueltig(true);
        entity.setBerechnetAm(LocalDateTime.now());

        return monatsSaldoRepository.save(entity);
    }

    // ==================== Invalidierung ====================

    /**
     * Invalidiert den Cache für einen bestimmten Monat.
     * Wird aufgerufen bei Änderungen an Zeitbuchungen oder Abwesenheiten.
     */
    @Transactional
    public void invalidiereMonat(Long mitarbeiterId, int jahr, int monat) {
        monatsSaldoRepository.invalidiere(mitarbeiterId, jahr, monat);
        log.debug("MonatsSaldo invalidiert: Mitarbeiter={}, {}/{}", mitarbeiterId, jahr, monat);
    }

    /**
     * Invalidiert den Cache für ein ganzes Jahr.
     * Wird aufgerufen bei Zeitkonto-Korrekturen (die jahresbezogen sind).
     */
    @Transactional
    public void invalidiereJahr(Long mitarbeiterId, int jahr) {
        monatsSaldoRepository.invalidiereJahr(mitarbeiterId, jahr);
        log.debug("MonatsSaldo invalidiert (ganzes Jahr): Mitarbeiter={}, {}", mitarbeiterId, jahr);
    }

    /**
     * Invalidiert ALLE Cache-Einträge für einen Mitarbeiter.
     * Wird aufgerufen bei Änderung der Zeitkonto-Sollstunden.
     */
    @Transactional
    public void invalidiereAlle(Long mitarbeiterId) {
        monatsSaldoRepository.invalidiereAlle(mitarbeiterId);
        log.debug("MonatsSaldo invalidiert (alle): Mitarbeiter={}", mitarbeiterId);
    }

    /**
     * Invalidiert den Cache basierend auf einem Zeitbuchungs-Datum.
     * Ermittelt automatisch Jahr/Monat aus dem Datum.
     */
    @Transactional
    public void invalidiereFuerDatum(Long mitarbeiterId, LocalDate datum) {
        if (datum != null) {
            invalidiereMonat(mitarbeiterId, datum.getYear(), datum.getMonthValue());
        }
    }

    /**
     * Invalidiert den Cache basierend auf einem DateTime (z.B. Zeitbuchung.startZeit).
     */
    @Transactional
    public void invalidiereFuerDateTime(Long mitarbeiterId, LocalDateTime dateTime) {
        if (dateTime != null) {
            invalidiereFuerDatum(mitarbeiterId, dateTime.toLocalDate());
        }
    }

    // ==================== Hilfsmethoden ====================

    /**
     * Berechnet die Stunden für bezahlte Feiertage in einem Zeitraum.
     * Nur Feiertage an Arbeitstagen (Sollstunden > 0) werden gezählt.
     * Halbe Feiertage (z.B. Heiligabend) zählen 50%.
     */
    private BigDecimal berechneFeiertagsStunden(Zeitkonto zeitkonto, LocalDate von, LocalDate bis) {
        BigDecimal summe = BigDecimal.ZERO;

        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            int wochentag = tag.getDayOfWeek().getValue();
            BigDecimal tagesSoll = zeitkonto.getSollstundenFuerTag(wochentag);

            if (tagesSoll.compareTo(BigDecimal.ZERO) > 0 && feiertagService.istFeiertag(tag)) {
                if (feiertagService.istHalberFeiertag(tag)) {
                    summe = summe.add(tagesSoll.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
                } else {
                    summe = summe.add(tagesSoll);
                }
            }
        }

        return summe;
    }
}
