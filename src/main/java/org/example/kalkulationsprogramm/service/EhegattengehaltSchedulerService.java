package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Monatlicher Scheduler: bucht das Ehegattengehalt automatisch, sobald der
 * konfigurierte Stichtag erreicht ist. Buchungskonto ist hart "4120 Loehne &
 * Gehaelter" (siehe V307) — kein UI-Auswahlfeld; reine Buchhaltung, keine
 * Kostenstelle. Falls der Bar-Saldo nicht reicht, wird im
 * KasseShortcutService.lohnZahlung() vorher automatisch eine Privateinlage
 * gebucht — Ziel: am Monatsende so wenig Bar in der Kasse wie noetig.
 *
 * Idempotenz: pro YYYY-MM nur einmal buchen. Wird ueber
 * KasseEinstellung.letzteBuchungJahrmonat sichergestellt — auch wenn der
 * Server neu startet oder der Cron mehrfach laeuft.
 *
 * Laufintervall: jeden Tag um 06:30 Uhr (vor Geschaeftszeiten, damit der
 * Saldo "frisch" ist).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EhegattengehaltSchedulerService {

    /** Standard-Lohn-Sachkonto aus V307 (SKR Handwerker). */
    public static final String LOHN_SACHKONTO_NUMMER = "4120";

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final KasseEinstellungRepository kasseEinstellungRepository;
    private final KasseShortcutService kasseShortcutService;
    private final SachkontoRepository sachkontoRepository;

    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Berlin")
    public void monatlicheLohnBuchung() {
        // Bewusst KEIN @Transactional auf dem Cron-Entry-Point: die Idempotenz-
        // Sperre (letzteBuchungJahrmonat) muss in einer EIGENEN Tx COMMITED sein,
        // BEVOR die Buchung passiert — sonst rollt ein Fehler in lohnZahlung()
        // sowohl die Belege als auch die Sperre zurueck, und der naechste Tag
        // bucht erneut. Daher delegiert dieser Wrapper an tryLohnBuchung(), das
        // intern zwei separate Tx-Aufrufe macht (Sperre setzen, dann buchen).
        try {
            tryLohnBuchung(LocalDate.now());
        } catch (org.springframework.dao.DataIntegrityViolationException
                | org.springframework.orm.jpa.JpaSystemException e) {
            // Konfigurationsfehler (z.B. nicht existierendes Sachkonto) sollen
            // sichtbar bleiben — Operator muss reagieren. Trotzdem Cron nicht
            // crashen lassen, sonst killt jeder Crash weitere Scheduler.
            log.error("Ehegattengehalt-Scheduler: Konfigurationsproblem — bitte Einstellungen pruefen", e);
        } catch (Exception e) {
            log.error("Ehegattengehalt-Scheduler fehlgeschlagen", e);
        }
    }

    /**
     * Paketoeffentliche Methode fuer Tests — gleiche Logik, beliebiges Datum.
     *
     * Ablauf: erst pruefen, dann Idempotenz-Sperre setzen + COMMIT, dann buchen.
     * Damit kann eine fehlgeschlagene Lohnbuchung NICHT mehr dazu fuehren, dass
     * derselbe Monat am naechsten Tag erneut versucht wird (Doppel-Lohn-Risiko).
     */
    public boolean tryLohnBuchung(LocalDate heute) {
        KasseEinstellung k = kasseEinstellungRepository.findSingleton().orElse(null);
        if (k == null || !k.isEhegattengehaltAktiv()) {
            return false;
        }
        if (k.getEhegattengehaltBetrag() == null || k.getEhegattengehaltBetrag().signum() <= 0) {
            log.warn("Ehegattengehalt aktiv aber kein gueltiger Betrag konfiguriert");
            return false;
        }
        if (k.getEhegattengehaltTag() == null) {
            log.warn("Ehegattengehalt aktiv aber kein Stichtag konfiguriert");
            return false;
        }
        Sachkonto lohnKonto = sachkontoRepository.findByNummer(LOHN_SACHKONTO_NUMMER).orElse(null);
        if (lohnKonto == null) {
            // V307 legt 4120 als Standard an — fehlt nur, wenn der Operator es
            // explizit geloescht hat. Nicht crashen, nur warnen + skippen.
            log.warn("Ehegattengehalt: Sachkonto {} (Loehne & Gehaelter) nicht gefunden — bitte in Sachkonten anlegen",
                    LOHN_SACHKONTO_NUMMER);
            return false;
        }

        if (heute.getDayOfMonth() < k.getEhegattengehaltTag()) {
            return false;
        }

        String aktuellerJahrmonat = YearMonth.from(heute).format(YEAR_MONTH);
        if (aktuellerJahrmonat.equals(k.getLetzteBuchungJahrmonat())) {
            return false;
        }

        // SCHRITT 1 (eigene Tx): Idempotenz-Sperre setzen und committen — bevor
        // der eigentliche Lohn fliesst. Wenn die nachfolgende Lohn-Buchung
        // failed, ist trotzdem fuer DIESEN Monat keine Doppel-Buchung mehr
        // moeglich. Der Admin sieht den Fehler im Log und kann manuell nach-
        // buchen — besser als doppeltes Gehalt.
        markiereJahrmonatGebucht(k.getId(), aktuellerJahrmonat);

        log.info("Ehegattengehalt-Scheduler: buche {} EUR fuer {} (Empfaenger: {})",
                k.getEhegattengehaltBetrag(), aktuellerJahrmonat, k.getEhegattengehaltEmpfaengerName());

        // SCHRITT 2 (eigene Tx via kasseShortcutService): Belege anlegen.
        // Kostenstelle = null: Ehegattengehalt ist reine Buchhaltung.
        kasseShortcutService.lohnZahlung(
                k.getEhegattengehaltBetrag(),
                heute,
                k.getEhegattengehaltEmpfaengerName() != null
                        ? "Auto: " + k.getEhegattengehaltEmpfaengerName()
                        : "Auto: Ehegattengehalt",
                lohnKonto,
                null,
                null);
        return true;
    }

    /**
     * Setzt die Idempotenz-Sperre. Wird vor der Lohn-Buchung aufgerufen, damit
     * ein fehlgeschlagener Lohn nicht zu einer Doppelbuchung am naechsten Tag
     * fuehrt. Bewusst KEIN @Transactional auf dieser Methode: Self-Invocation
     * aus tryLohnBuchung() wuerde Spring-AOP umgehen — Spring Data save()
     * oeffnet seine eigene Tx und committed beim Return, das reicht.
     */
    void markiereJahrmonatGebucht(Long einstellungsId, String jahrmonat) {
        KasseEinstellung k = kasseEinstellungRepository.findById(einstellungsId).orElseThrow();
        k.setLetzteBuchungJahrmonat(jahrmonat);
        kasseEinstellungRepository.save(k);
    }
}
