package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service für Urlaubsverfall-Logik.
 * 
 * Regelung: Resturlaub aus dem Vorjahr verfällt am 1. Februar des Folgejahres.
 * Warnung wird 2 Monate vorher angezeigt (ab 1. Dezember).
 */
@Service
@RequiredArgsConstructor
public class UrlaubsverfallService {

    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * Prüft ob ein Mitarbeiter eine Urlaubsverfall-Warnung erhalten sollte.
     * 
     * Warnung wird angezeigt wenn:
     * - Der Mitarbeiter Resturlaub aus dem Vorjahr hat
     * - Das aktuelle Datum zwischen 1. Dezember und 31. Januar liegt
     * 
     * @param mitarbeiterId ID des Mitarbeiters
     * @return Optional mit Warnungsdaten, oder empty wenn keine Warnung nötig
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> pruefeVerfallWarnung(Long mitarbeiterId) {
        LocalDate heute = LocalDate.now();

        // Prüfe ob wir im Warnzeitraum sind (1. Dezember bis 31. Januar)
        boolean imWarnzeitraum = isImWarnzeitraum(heute);
        if (!imWarnzeitraum) {
            return Optional.empty();
        }

        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        if (mitarbeiter == null) {
            return Optional.empty();
        }

        Integer resturlaub = mitarbeiter.getResturlaubVorjahr();
        if (resturlaub == null || resturlaub <= 0) {
            return Optional.empty();
        }

        // Berechne das Verfallsdatum (1. Februar des aktuellen/nächsten Jahres)
        LocalDate verfallsDatum = berechneVerfallsDatum(heute);
        long tageVerbleibend = java.time.temporal.ChronoUnit.DAYS.between(heute, verfallsDatum);

        Map<String, Object> warnung = new LinkedHashMap<>();
        warnung.put("resturlaubTage", resturlaub);
        warnung.put("verfallsDatum", verfallsDatum.toString());
        warnung.put("verfallsJahr", verfallsDatum.getYear());
        warnung.put("tageVerbleibend", tageVerbleibend);
        warnung.put("dringend", tageVerbleibend <= 30); // Letzte 30 Tage = dringend

        return Optional.of(warnung);
    }

    /**
     * Prüft ob ein Mitarbeiter (per Token) eine Urlaubsverfall-Warnung erhalten
     * sollte.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> pruefeVerfallWarnungByToken(String token) {
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .flatMap(m -> pruefeVerfallWarnung(m.getId()));
    }

    /**
     * Prüft ob das aktuelle Datum im Warnzeitraum liegt.
     * Warnzeitraum: 1. Dezember bis 31. Januar (2 Monate vor Verfall am 1.2.)
     */
    private boolean isImWarnzeitraum(LocalDate datum) {
        Month monat = datum.getMonth();
        return monat == Month.DECEMBER || monat == Month.JANUARY;
    }

    /**
     * Berechnet das Verfallsdatum des Resturlaubs.
     * - Im Dezember: 1. Februar des nächsten Jahres
     * - Im Januar: 1. Februar des aktuellen Jahres
     */
    private LocalDate berechneVerfallsDatum(LocalDate datum) {
        int jahr = datum.getYear();
        if (datum.getMonth() == Month.DECEMBER) {
            // Dezember -> Verfall am 1.2. des nächsten Jahres
            return LocalDate.of(jahr + 1, 2, 1);
        } else {
            // Januar -> Verfall am 1.2. des aktuellen Jahres
            return LocalDate.of(jahr, 2, 1);
        }
    }

    /**
     * Führt den jährlichen Urlaubsübertrag durch.
     * Sollte am 1. Januar ausgeführt werden (z.B. via Scheduler).
     * 
     * - Berechnet verbleibenden Urlaubsanspruch des Vorjahres
     * - Setzt diesen als resturlaubVorjahr
     * - Dieser verfällt dann am 1.2.
     */
    @Transactional
    public void fuehreJaehrlichenUebertragDurch(Long mitarbeiterId, int verbleibendeTage) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        mitarbeiter.setResturlaubVorjahr(verbleibendeTage);
        mitarbeiterRepository.save(mitarbeiter);
    }

    /**
     * Löscht den Resturlaub am 1. Februar (Verfall).
     * Sollte am 1. Februar ausgeführt werden (z.B. via Scheduler).
     */
    @Transactional
    public void loescheVerfallenenResturlaub(Long mitarbeiterId) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        mitarbeiter.setResturlaubVorjahr(0);
        mitarbeiterRepository.save(mitarbeiter);
    }
}
