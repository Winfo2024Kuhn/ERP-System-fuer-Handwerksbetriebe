package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Beendet automatisch offene Zeitbuchungen, die das konfigurierte
 * Buchungszeitfenster überschritten haben (z.B. Mitarbeiter hat vergessen abzustechen).
 *
 * Läuft alle 5 Minuten und prüft:
 * 1. Ob eine offene Buchung über Mitternacht hinaus läuft → sofort stoppen bei 23:59
 * 2. Ob eine offene Buchung nach der konfigurierten buchungEndeZeit läuft → stoppen bei buchungEndeZeit
 */
@Service
@RequiredArgsConstructor
public class ZeitbuchungAutoStopService {

    private static final Logger log = LoggerFactory.getLogger(ZeitbuchungAutoStopService.class);

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitkontoRepository zeitkontoRepository;
    private final ZeitbuchungAuditService auditService;
    private final MonatsSaldoService monatsSaldoService;

    /**
     * Prüft alle 5 Minuten auf offene Buchungen, die automatisch beendet werden müssen.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void pruefUndStoppeOffeneBuchungen() {
        List<Zeitkonto> zeitkonten = zeitkontoRepository.findAll();

        for (Zeitkonto konto : zeitkonten) {
            Long mitarbeiterId = konto.getMitarbeiter().getId();

            List<Zeitbuchung> offene = zeitbuchungRepository
                    .findByMitarbeiterIdAndEndeZeitIsNull(mitarbeiterId);

            for (Zeitbuchung buchung : offene) {
                autoStoppeWennNoetig(buchung, konto);
            }
        }
    }

    @Transactional
    void autoStoppeWennNoetig(Zeitbuchung buchung, Zeitkonto konto) {
        LocalDateTime jetzt = LocalDateTime.now();
        LocalDate startDatum = buchung.getStartZeit().toLocalDate();
        LocalDate heute = jetzt.toLocalDate();

        // 1. Buchung läuft über Mitternacht → Stoppe bei 23:59 des Start-Tages
        if (heute.isAfter(startDatum)) {
            LocalDateTime endeZeit = startDatum.atTime(23, 59, 0);
            stopBuchung(buchung, endeZeit, "Automatisch beendet: Buchung lief über Mitternacht hinaus");
            log.info("Auto-Stop (Mitternacht): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                    buchung.getId(), konto.getMitarbeiter().getId(), endeZeit);
            return;
        }

        // 2. Soll-Endezeit des Zeitkontos überschritten
        LocalTime endeZeit = konto.getBuchungEndeZeit();
        if (endeZeit != null && jetzt.toLocalTime().isAfter(endeZeit)) {
            LocalDateTime stopZeit = heute.atTime(endeZeit);
            // Nur stoppen, wenn die Buchung VOR der Endezeit gestartet wurde
            if (buchung.getStartZeit().isBefore(stopZeit)) {
                stopBuchung(buchung, stopZeit, "Automatisch beendet: Buchungszeitfenster überschritten (Ende: " + endeZeit + ")");
                log.info("Auto-Stop (Zeitfenster): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                        buchung.getId(), konto.getMitarbeiter().getId(), stopZeit);
            }
        }
    }

    private void stopBuchung(Zeitbuchung buchung, LocalDateTime endeZeit, String grund) {
        buchung.setEndeZeit(endeZeit);

        Duration dauer = Duration.between(buchung.getStartZeit(), endeZeit);
        BigDecimal stunden = BigDecimal.valueOf(dauer.toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        buchung.setAnzahlInStunden(stunden);

        buchung.markiereAlsGeaendert(buchung.getMitarbeiter());
        auditService.protokolliereAenderung(buchung, buchung.getMitarbeiter(),
                ErfassungsQuelle.SYSTEM, grund);

        zeitbuchungRepository.save(buchung);
        monatsSaldoService.invalidiereFuerDateTime(buchung.getMitarbeiter().getId(), buchung.getStartZeit());
    }
}
