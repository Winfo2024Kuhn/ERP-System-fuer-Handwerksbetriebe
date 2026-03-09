package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Befüllt den MonatsSaldo-Cache beim Anwendungsstart für alle aktiven Mitarbeiter.
 * 
 * Läuft asynchron nach dem vollständigen Start der Anwendung.
 * Berechnet alle vergangenen Monate vom Eintrittsdatum (oder erster Buchung)
 * bis zum Vormonat und speichert sie im Cache.
 * Bereits gültige Cache-Einträge werden übersprungen.
 */
@Service
@RequiredArgsConstructor
public class MonatsSaldoWarmupService {

    private static final Logger log = LoggerFactory.getLogger(MonatsSaldoWarmupService.class);

    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final MonatsSaldoRepository monatsSaldoRepository;
    private final MonatsSaldoService monatsSaldoService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("MonatsSaldo-Cache Warmup gestartet...");
        long startTime = System.currentTimeMillis();

        List<Mitarbeiter> aktiveMitarbeiter = mitarbeiterRepository.findByAktivTrue();
        int gesamtBerechnet = 0;
        int gesamtUebersprungen = 0;

        YearMonth aktuellerMonat = YearMonth.now();

        for (Mitarbeiter mitarbeiter : aktiveMitarbeiter) {
            try {
                int[] counts = warmupFuerMitarbeiter(mitarbeiter, aktuellerMonat);
                gesamtBerechnet += counts[0];
                gesamtUebersprungen += counts[1];
            } catch (Exception e) {
                log.warn("MonatsSaldo-Warmup fehlgeschlagen für Mitarbeiter {} (ID={}): {}",
                        mitarbeiter.getVorname() + " " + mitarbeiter.getNachname(),
                        mitarbeiter.getId(), e.getMessage());
            }
        }

        long dauer = System.currentTimeMillis() - startTime;
        log.info("MonatsSaldo-Cache Warmup abgeschlossen: {} Mitarbeiter, {} Monate berechnet, {} übersprungen, Dauer: {}ms",
                aktiveMitarbeiter.size(), gesamtBerechnet, gesamtUebersprungen, dauer);
    }

    private int[] warmupFuerMitarbeiter(Mitarbeiter mitarbeiter, YearMonth aktuellerMonat) {
        int berechnet = 0;
        int uebersprungen = 0;

        // Startdatum bestimmen: erste Buchung, Eintrittsdatum nur als Fallback
        LocalDate startDatum = null;
        Optional<Zeitbuchung> ersteBuchung = zeitbuchungRepository
                .findFirstByMitarbeiterIdOrderByStartZeitAsc(mitarbeiter.getId());
        if (ersteBuchung.isPresent()) {
            startDatum = ersteBuchung.get().getStartZeit().toLocalDate();
        } else if (mitarbeiter.getEintrittsdatum() != null) {
            startDatum = mitarbeiter.getEintrittsdatum();
        } else {
            // Keine Buchungen und kein Eintrittsdatum → nichts zu cachen
            return new int[]{0, 0};
        }

        YearMonth startYM = YearMonth.from(startDatum);
        // Nur bis zum Vormonat (aktueller Monat wird immer live berechnet)
        YearMonth endeYM = aktuellerMonat.minusMonths(1);

        if (startYM.isAfter(endeYM)) {
            return new int[]{0, 0};
        }

        for (YearMonth ym = startYM; !ym.isAfter(endeYM); ym = ym.plusMonths(1)) {
            // Prüfen ob bereits ein gültiger Cache-Eintrag existiert
            boolean istGueltig = monatsSaldoRepository
                    .findByMitarbeiterIdAndJahrAndMonat(mitarbeiter.getId(), ym.getYear(), ym.getMonthValue())
                    .map(ms -> Boolean.TRUE.equals(ms.getGueltig()))
                    .orElse(false);

            if (istGueltig) {
                uebersprungen++;
            } else {
                monatsSaldoService.getOrBerechne(mitarbeiter.getId(), ym.getYear(), ym.getMonthValue());
                berechnet++;
            }
        }

        if (berechnet > 0) {
            log.debug("Warmup für {} {}: {} Monate berechnet, {} übersprungen",
                    mitarbeiter.getVorname(), mitarbeiter.getNachname(), berechnet, uebersprungen);
        }

        return new int[]{berechnet, uebersprungen};
    }
}
