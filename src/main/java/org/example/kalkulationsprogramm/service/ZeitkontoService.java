package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service für Zeitkonten (Sollstunden pro Mitarbeiter).
 */
@Service
@RequiredArgsConstructor
public class ZeitkontoService {

    private final ZeitkontoRepository zeitkontoRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final TagesSollService tagesSollService;

    /**
     * Gibt das Zeitkonto für einen Mitarbeiter zurück.
     * Erstellt automatisch ein Standard-Zeitkonto wenn noch keines existiert.
     */
    @Transactional
    public Zeitkonto getOrCreateZeitkonto(Long mitarbeiterId) {
        return zeitkontoRepository.findByMitarbeiterId(mitarbeiterId)
                .orElseGet(() -> {
                    Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));
                    Zeitkonto neuesKonto = new Zeitkonto(mitarbeiter);
                    return zeitkontoRepository.save(neuesKonto);
                });
    }

    /**
     * Gibt alle Zeitkonten zurück.
     */
    public List<Zeitkonto> getAlleZeitkonten() {
        return zeitkontoRepository.findAll();
    }

    /**
     * Speichert ein Zeitkonto.
     */
    @Transactional
    public Zeitkonto speichereZeitkonto(Zeitkonto zeitkonto) {
        return zeitkontoRepository.save(zeitkonto);
    }

    /**
     * Aktualisiert die Sollstunden für einen Mitarbeiter.
     */
    @Transactional
    public Zeitkonto aktualisiereZeitkonto(Long mitarbeiterId,
            BigDecimal montag, BigDecimal dienstag,
            BigDecimal mittwoch, BigDecimal donnerstag,
            BigDecimal freitag, BigDecimal samstag,
            BigDecimal sonntag) {
        Zeitkonto konto = getOrCreateZeitkonto(mitarbeiterId);
        konto.setMontagStunden(montag);
        konto.setDienstagStunden(dienstag);
        konto.setMittwochStunden(mittwoch);
        konto.setDonnerstagStunden(donnerstag);
        konto.setFreitagStunden(freitag);
        konto.setSamstagStunden(samstag);
        konto.setSonntagStunden(sonntag);
        return zeitkontoRepository.save(konto);
    }

    /**
     * Berechnet die Sollstunden für einen Monat unter Berücksichtigung von
     * Feiertagen und halben Feiertagen (z.B. Heiligabend, Silvester).
     */
    public BigDecimal berechneSollstundenFuerMonat(Long mitarbeiterId, int jahr, int monat) {
        Zeitkonto konto = getOrCreateZeitkonto(mitarbeiterId);

        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = ersterTag.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        return berechneSollstundenFuerZeitraum(konto, ersterTag, letzterTag);
    }

    /**
     * Berechnet die Sollstunden für einen Monat bis heute unter Berücksichtigung
     * von
     * Feiertagen und halben Feiertagen.
     */
    public BigDecimal berechneSollstundenFuerMonatBisHeute(Long mitarbeiterId, int jahr, int monat) {
        Zeitkonto konto = getOrCreateZeitkonto(mitarbeiterId);

        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate heute = LocalDate.now();
        LocalDate letzterTag = (monat == heute.getMonthValue() && jahr == heute.getYear())
                ? heute
                : ersterTag.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        return berechneSollstundenFuerZeitraum(konto, ersterTag, letzterTag);
    }

    /**
     * Berechnung: Sollstunden für einen Zeitraum.
     * Feiertage zählen weiter als bezahlte Arbeitstage mit den vollen
     * Sollstunden, halbe Feiertage (z.B. Heiligabend, Silvester) mit 50%.
     * Zusätzlich richtet sich das Soll jetzt nach einer laufenden
     * Wiedereingliederung, falls für den Mitarbeiter eine läuft.
     * Die eigentliche Rechenregel steckt in {@link TagesSollService#periodenSollSumme}.
     */
    public BigDecimal berechneSollstundenFuerZeitraum(Zeitkonto konto, LocalDate von, LocalDate bis) {
        Mitarbeiter mitarbeiter = konto.getMitarbeiter();
        Long mitarbeiterId = mitarbeiter != null ? mitarbeiter.getId() : null;
        return tagesSollService.periodenSollSumme(mitarbeiterId, konto, von, bis);
    }
}
