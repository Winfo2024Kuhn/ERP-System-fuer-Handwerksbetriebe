package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Service für Abwesenheiten (Urlaub, Krankheit, Fortbildung).
 * Ermöglicht direkte Buchung ohne Urlaubsantrag (für PC-Frontend).
 */
@Service
@RequiredArgsConstructor
public class AbwesenheitService {

    private final AbwesenheitRepository abwesenheitRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitkontoService zeitkontoService;
    private final FeiertagService feiertagService;
    private final MonatsSaldoService monatsSaldoService;

    /**
     * Bucht eine Abwesenheit für einen Mitarbeiter an einem bestimmten Tag.
     * Prüft automatisch ob der Tag ein Arbeitstag ist und berechnet die Stunden.
     *
     * @param mitarbeiterId ID des Mitarbeiters
     * @param datum         Tag der Abwesenheit
     * @param typ           Typ der Abwesenheit (URLAUB, KRANKHEIT, etc.)
     * @param halberTag     true = nur 50% der Sollstunden
     * @return Die erstellte Abwesenheit
     * @throws IllegalArgumentException wenn Mitarbeiter nicht gefunden oder kein
     *                                  Arbeitstag
     */
    @Transactional
    public Abwesenheit bucheAbwesenheit(Long mitarbeiterId, LocalDate datum, AbwesenheitsTyp typ, boolean halberTag) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));

        // Prüfe ob bereits Abwesenheit für diesen Tag existiert
        if (abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(mitarbeiterId, datum, typ)) {
            throw new IllegalStateException("Für diesen Tag existiert bereits eine " + typ + "-Buchung");
        }

        // Prüfe ob Feiertag
        if (feiertagService.istFeiertag(datum)) {
            throw new IllegalArgumentException("An Feiertagen kann keine Abwesenheit gebucht werden: " +
                    feiertagService.getFeiertagInfo(datum).map(Feiertag::getBezeichnung).orElse(datum.toString()));
        }

        // Hole Sollstunden für diesen Tag aus dem Zeitkonto
        Zeitkonto zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId);
        int wochentag = datum.getDayOfWeek().getValue(); // 1=Montag, 7=Sonntag
        BigDecimal sollStunden = zeitkonto.getSollstundenFuerTag(wochentag);

        // Prüfe ob Arbeitstag (Sollstunden > 0)
        if (sollStunden.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Kein Arbeitstag: Am " +
                    datum.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.GERMAN) +
                    " hat dieser Mitarbeiter keine Sollstunden");
        }

        // Bei halbem Tag nur 50% der Stunden
        BigDecimal stundenZuBuchen = halberTag
                ? sollStunden.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                : sollStunden;

        // ZEITAUSGLEICH: Prüfe ob genügend Überstunden vorhanden
        if (typ == AbwesenheitsTyp.ZEITAUSGLEICH) {
            BigDecimal aktuellerSaldo = berechneAktuellenSaldo(mitarbeiterId);
            if (aktuellerSaldo.compareTo(stundenZuBuchen) < 0) {
                throw new IllegalStateException("Nicht genügend Überstunden für Zeitausgleich. " +
                        "Aktueller Saldo: " + aktuellerSaldo.setScale(1, RoundingMode.HALF_UP) + "h, " +
                        "benötigt: " + stundenZuBuchen.setScale(1, RoundingMode.HALF_UP) + "h");
            }
        }

        // Abwesenheit erstellen
        Abwesenheit abwesenheit = new Abwesenheit();
        abwesenheit.setMitarbeiter(mitarbeiter);
        abwesenheit.setDatum(datum);
        abwesenheit.setTyp(typ);
        abwesenheit.setStunden(stundenZuBuchen);
        abwesenheit.setNotiz(halberTag ? "Halber Tag (manuell gebucht)" : "Manuell gebucht");

        Abwesenheit gespeichert = abwesenheitRepository.save(abwesenheit);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum);

        return gespeichert;
    }

    /**
     * Berechnet den aktuellen Stundensaldo eines Mitarbeiters.
     * Positiv = Überstunden, Negativ = Fehlstunden.
     */
    private BigDecimal berechneAktuellenSaldo(Long mitarbeiterId) {
        LocalDate heute = LocalDate.now();
        int currentYear = heute.getYear();
        int currentMonth = heute.getMonthValue();

        // Vereinfachte Berechnung: Summe der Überstundensalden pro Monat
        // Für genauere Berechnung könnte ZeiterfassungApiService.getSaldo() verwendet
        // werden
        BigDecimal saldo = BigDecimal.ZERO;
        for (int m = 1; m <= currentMonth; m++) {
            BigDecimal sollMonat = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, currentYear, m);
            saldo = saldo.subtract(sollMonat);
        }

        // Abwesenheitsstunden addieren (zählen als gearbeitet)
        LocalDate jahresanfang = LocalDate.of(currentYear, 1, 1);
        BigDecimal abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                mitarbeiterId, jahresanfang, heute);
        saldo = saldo.add(abwesenheitsStunden);

        return saldo;
    }

    /**
     * Löscht eine Abwesenheit.
     */
    @Transactional
    public void loescheAbwesenheit(Long abwesenheitId) {
        Abwesenheit abwesenheit = abwesenheitRepository.findById(abwesenheitId)
                .orElseThrow(() -> new IllegalArgumentException("Abwesenheit nicht gefunden: " + abwesenheitId));

        Long mitarbeiterId = abwesenheit.getMitarbeiter().getId();
        LocalDate datum = abwesenheit.getDatum();

        abwesenheitRepository.deleteById(abwesenheitId);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum);
    }

    /**
     * Gibt alle Abwesenheiten eines Mitarbeiters zurück.
     */
    public List<Abwesenheit> getAbwesenheitenByMitarbeiter(Long mitarbeiterId) {
        return abwesenheitRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId);
    }

    /**
     * Gibt Abwesenheiten eines Mitarbeiters für einen Zeitraum zurück.
     */
    public List<Abwesenheit> getAbwesenheitenByMitarbeiterAndZeitraum(Long mitarbeiterId, LocalDate von,
            LocalDate bis) {
        return abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis);
    }

    /**
     * Gibt alle Abwesenheiten aller Mitarbeiter für einen Zeitraum zurück (für
     * Team-Kalender).
     */
    public List<Abwesenheit> getAllAbwesenheitenForZeitraum(LocalDate von, LocalDate bis) {
        return abwesenheitRepository.findAllByDatumBetween(von, bis);
    }
}
