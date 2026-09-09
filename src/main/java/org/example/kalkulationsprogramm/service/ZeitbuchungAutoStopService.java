package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
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
 * 1. Das Buchungszeitfenster der am Buchungstag gültigen Version.
 * 2. Ohne passendes Zeitfenster: Mitternachts-Sicherung bei 23:59 des Starttags.
 */
@Service
@RequiredArgsConstructor
public class ZeitbuchungAutoStopService {

    private static final Logger log = LoggerFactory.getLogger(ZeitbuchungAutoStopService.class);

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitkontoVersionRepository zeitkontoVersionRepository;
    private final ZeitbuchungAuditService auditService;
    private final MonatsSaldoService monatsSaldoService;

    /**
     * Prüft alle 5 Minuten auf offene Buchungen, die automatisch beendet werden müssen.
     *
     * Die Transaktionsgrenze sitzt bewusst HIER und nicht auf {@link #autoStoppeWennNoetig}:
     * Diese Methode wird vom Scheduler über den Spring-Proxy aufgerufen, {@code autoStoppeWennNoetig}
     * dagegen nur intern per {@code this}-Aufruf – ein {@code @Transactional} dort würde vom
     * Proxy nicht gesehen und liefe wirkungslos ins Leere (Save, Audit-Eintrag und
     * Saldo-Invalidierung wären dann nicht atomar).
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void pruefUndStoppeOffeneBuchungen() {
        List<Zeitbuchung> offene = zeitbuchungRepository.findByEndeZeitIsNull();
        Map<Long, Map<LocalDate, Optional<ZeitkontoVersion>>> versionen = new HashMap<>();
        for (Zeitbuchung buchung : offene) {
            if (buchung.getMitarbeiter() == null || buchung.getStartZeit() == null) {
                log.warn("Auto-Stop: Buchung {} ohne Mitarbeiter oder Startzeit übersprungen", buchung.getId());
                continue;
            }
            if (buchung.getMitarbeiter().getArt() != MitarbeiterArt.MENSCH) continue;
            Long mitarbeiterId = buchung.getMitarbeiter().getId();
            LocalDate tag = buchung.getStartZeit().toLocalDate();
            // Mehrere offene Buchungen derselben Person am selben Tag teilen den Lookup.
            Optional<ZeitkontoVersion> version = versionen.computeIfAbsent(mitarbeiterId, id -> new HashMap<>())
                    .computeIfAbsent(tag, datum -> zeitkontoVersionRepository.findAm(mitarbeiterId, datum));
            autoStoppeWennNoetig(buchung, version.orElse(null));
        }
    }

    // Kein @Transactional: siehe Hinweis an pruefUndStoppeOffeneBuchungen().
    void autoStoppeWennNoetig(Zeitbuchung buchung, ZeitkontoVersion konto) {
        if (buchung.getEndeZeit() != null || buchung.getStartZeit() == null
                || buchung.getMitarbeiter() == null
                || buchung.getMitarbeiter().getArt() != MitarbeiterArt.MENSCH) return;
        LocalDateTime jetzt = LocalDateTime.now();
        LocalDate startDatum = buchung.getStartZeit().toLocalDate();

        // Das Zeitfenster gehört zum Buchungstag, auch bei einem späteren Scheduler-Lauf.
        LocalTime endeZeit = konto == null ? null : konto.getBuchungEndeZeit();
        if (endeZeit != null) {
            LocalDateTime stopZeit = startDatum.atTime(endeZeit);
            if (jetzt.isAfter(stopZeit) && buchung.getStartZeit().isBefore(stopZeit)) {
                stopBuchung(buchung, stopZeit,
                        "Automatisch beendet: Buchungszeitfenster überschritten (Ende: " + endeZeit + ")");
                log.info("Auto-Stop (Zeitfenster): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                        buchung.getId(), buchung.getMitarbeiter().getId(), stopZeit);
                return;
            }
        }

        // Ohne Version/Endezeit bleibt die Mitternachts-Sicherung aktiv, auch
        // wenn Zeitkonto oder Beschäftigung inzwischen ausgeschaltet wurden.
        if (jetzt.toLocalDate().isAfter(startDatum)) {
            LocalDateTime stopZeit = startDatum.atTime(23, 59, 0);
            // Start in der letzten Minute darf keine negative Dauer erzeugen.
            if (stopZeit.isBefore(buchung.getStartZeit())) stopZeit = buchung.getStartZeit();
            stopBuchung(buchung, stopZeit, "Automatisch beendet: Buchung lief über Mitternacht hinaus");
            log.info("Auto-Stop (Mitternacht): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                    buchung.getId(), buchung.getMitarbeiter().getId(), stopZeit);
        }
    }

    private void stopBuchung(Zeitbuchung buchung, LocalDateTime endeZeit, String grund) {
        buchung.setEndeZeit(endeZeit);
        // Die Endezeit ist geschätzt, nicht gestempelt. Markieren, damit sie in der
        // Benachrichtigungs-Glocke als Prüffall auftaucht und ein verspätet
        // eintreffender Feierabend-Stop vom Handy sie noch korrigieren darf.
        buchung.setAutomatischBeendet(true);

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
