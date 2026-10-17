package org.example.kalkulationsprogramm.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.domain.ZeitHinweisGesendet;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ZeitHinweisGesendetRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/**
 * Erinnert Mitarbeiter per Push aufs Handy an ihre Zeiterfassung:
 * <ul>
 *   <li><b>Kein Feierabend gestempelt:</b> Der Auto-Stop hat eine vergessene Buchung
 *       beendet (dieselbe Meldung, die das Büro unter „Zeiten prüfen“ sieht).</li>
 *   <li><b>Pause nach Arbeitszeitgesetz (§ 4 ArbZG):</b> Mehr als 6 Stunden Arbeit
 *       verlangen insgesamt 30 Minuten Pause, mehr als 9 Stunden 45 Minuten, und
 *       niemand darf länger als 6 Stunden am Stück arbeiten. Mit zählen nur
 *       Unterbrechungen von mindestens 15 Minuten. Die Erinnerung kommt 15 Minuten
 *       vor der Grenze, damit die Pause noch rechtzeitig klappt.</li>
 * </ul>
 * Die Nachrichten gehen nur an die betroffene Person, kommen je Tag und Art höchstens
 * einmal und ändern keine Zeiten. Nachts (22–6 Uhr) schickt der Job nichts: Ein in der
 * Nacht beendeter Feierabend wird morgens gemeldet.
 */
@Service
@RequiredArgsConstructor
public class ZeitHinweisService {

    private static final Logger log = LoggerFactory.getLogger(ZeitHinweisService.class);

    static final String TYP_AUTO_BEENDET = "zeit-auto-beendet";
    static final String TYP_PAUSE = "pause";
    static final String URL_TAGESBUCHUNGEN = "/zeiterfassung/tagesbuchungen";
    static final String URL_ZEITERFASSUNG = "/zeiterfassung/zeiterfassung";

    /** So lange vor der gesetzlichen Grenze kommt die Pausen-Erinnerung. */
    static final Duration VORLAUF = Duration.ofMinutes(15);
    /** Kürzere Unterbrechungen zählen laut § 4 ArbZG nicht als Pause. */
    static final Duration MIN_PAUSENBLOCK = Duration.ofMinutes(15);
    /** Länger darf laut § 4 Satz 3 ArbZG niemand ohne Pause arbeiten. */
    static final Duration MAX_AM_STUECK = Duration.ofHours(6);
    /** Vor 6 und ab 22 Uhr klingelt kein Handy. */
    static final LocalTime RUHE_BIS = LocalTime.of(6, 0);
    static final LocalTime RUHE_AB = LocalTime.of(22, 0);
    /** Auto-Stops, die länger zurückliegen, sind schon gemeldet oder zu alt für eine Erinnerung. */
    static final Duration AUTO_STOP_FRIST = Duration.ofHours(12);

    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter UHRZEIT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** Höchste Grenze zuerst: Wer schon an der 9-Stunden-Grenze ist, braucht die 6-Stunden-Erinnerung nicht mehr. */
    private static final List<Stufe> STUFEN = List.of(
            new Stufe(ZeitHinweisGesendet.Art.PAUSE_9_STUNDEN, Duration.ofHours(9), Duration.ofMinutes(45)),
            new Stufe(ZeitHinweisGesendet.Art.PAUSE_6_STUNDEN, Duration.ofHours(6), Duration.ofMinutes(30)));

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitHinweisGesendetRepository gesendetRepository;
    private final WebPushService webPushService;

    private record Stufe(ZeitHinweisGesendet.Art art, Duration arbeit, Duration pause) {}

    /**
     * Gearbeitete Zeit, anrechenbare Pause und die Arbeit seit der letzten echten
     * Pause ({@code amStueck}) eines Tages bis jetzt.
     */
    record Tagesbilanz(Duration arbeit, Duration pause, Duration amStueck) {

        /**
         * Legt die Arbeitsbuchungen des Tages nebeneinander. Lücken dazwischen sind
         * Pausen – egal ob als Pause gestempelt, einfach ausgestempelt oder eine nicht
         * gebuchte Fahrt –, zählen aber nur ab {@link #MIN_PAUSENBLOCK}. Überlappende
         * Buchungen werden nicht doppelt gezählt.
         */
        static Tagesbilanz aus(List<Zeitbuchung> buchungen, LocalDateTime jetzt) {
            List<LocalDateTime[]> abschnitte = new ArrayList<>();
            for (Zeitbuchung b : buchungen) {
                if (b.getTyp() == BuchungsTyp.PAUSE || b.getStartZeit() == null) continue;
                LocalDateTime ende = b.getEndeZeit() == null || b.getEndeZeit().isAfter(jetzt) ? jetzt : b.getEndeZeit();
                if (ende.isAfter(b.getStartZeit())) abschnitte.add(new LocalDateTime[] {b.getStartZeit(), ende});
            }
            abschnitte.sort(Comparator.comparing(a -> a[0]));

            Duration arbeit = Duration.ZERO;
            Duration pause = Duration.ZERO;
            Duration amStueck = Duration.ZERO;
            LocalDateTime bisher = null;
            for (LocalDateTime[] a : abschnitte) {
                LocalDateTime von = a[0];
                if (bisher != null) {
                    if (von.isAfter(bisher)) {
                        Duration luecke = Duration.between(bisher, von);
                        if (luecke.compareTo(MIN_PAUSENBLOCK) >= 0) {
                            pause = pause.plus(luecke);
                            amStueck = Duration.ZERO;
                        }
                    } else {
                        von = bisher;
                    }
                }
                if (a[1].isAfter(von)) {
                    Duration dauer = Duration.between(von, a[1]);
                    arbeit = arbeit.plus(dauer);
                    amStueck = amStueck.plus(dauer);
                }
                if (bisher == null || a[1].isAfter(bisher)) bisher = a[1];
            }
            return new Tagesbilanz(arbeit, pause, amStueck);
        }
    }

    /** Alle 5 Minuten: vergessene Feierabende melden und an fällige Pausen erinnern. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    @Transactional
    public void pruefe() {
        pruefe(LocalDateTime.now());
    }

    // Kein @Transactional: läuft nur intern aus pruefe() (siehe ZeitbuchungAutoStopService).
    void pruefe(LocalDateTime jetzt) {
        // Aufräumen auch bei abgeschaltetem Push: Die Einträge sind personenbezogen.
        gesendetRepository.loescheAelterAls(jetzt.toLocalDate().minusDays(7));
        if (!webPushService.isEnabled()) return;
        LocalTime uhrzeit = jetzt.toLocalTime();
        if (uhrzeit.isBefore(RUHE_BIS) || !uhrzeit.isBefore(RUHE_AB)) return;
        meldeAutomatischBeendete(jetzt);
        pruefePausen(jetzt);
    }

    /**
     * Push „Kein Feierabend gestempelt“ für frisch vom Auto-Stop beendete Buchungen.
     * Wurde die Zeit inzwischen korrigiert, ist sie kein Prüffall mehr und es kommt
     * auch kein Hinweis.
     */
    private void meldeAutomatischBeendete(LocalDateTime jetzt) {
        LocalDateTime frist = jetzt.minus(AUTO_STOP_FRIST);
        for (Zeitbuchung z : zeitbuchungRepository
                .findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(jetzt.minusDays(2))) {
            Mitarbeiter mitarbeiter = z.getMitarbeiter();
            if (mitarbeiter == null || z.getEndeZeit() == null || !istAktiverMensch(mitarbeiter)
                    || z.getZuletztGeaendertAm() == null || z.getZuletztGeaendertAm().isBefore(frist)) continue;
            String text = String.format(
                    "Deine %s vom %s wurde automatisch um %s Uhr beendet. "
                            + "Bitte prüf die Zeit – stimmt sie nicht, sag im Büro Bescheid.",
                    z.getTyp() == BuchungsTyp.PAUSE ? "Pause" : "Arbeitszeit",
                    z.getStartZeit().format(TAG_FORMAT), z.getEndeZeit().format(UHRZEIT_FORMAT));
            // Absteigend sortiert: Bei mehreren Buchungen eines Tages gewinnt die letzte.
            merkeUndSende(mitarbeiter.getId(), z.getStartZeit().toLocalDate(), ZeitHinweisGesendet.Art.AUTO_BEENDET,
                    jetzt, "Kein Feierabend gestempelt", text, URL_TAGESBUCHUNGEN, TYP_AUTO_BEENDET);
        }
    }

    /** Wer arbeitet gerade und steuert auf eine Pausen-Grenze zu? */
    private void pruefePausen(LocalDateTime jetzt) {
        LocalDate heute = jetzt.toLocalDate();
        Set<Long> geprueft = new HashSet<>();
        for (Zeitbuchung offen : zeitbuchungRepository.findByEndeZeitIsNull()) {
            Mitarbeiter mitarbeiter = offen.getMitarbeiter();
            // Läuft gerade eine Pause, ist nichts zu erinnern. Über Mitternacht laufende
            // Buchungen beendet der Auto-Stop, nicht dieser Job.
            if (offen.getTyp() == BuchungsTyp.PAUSE || offen.getStartZeit() == null
                    || !offen.getStartZeit().toLocalDate().equals(heute)
                    || mitarbeiter == null || !istAktiverMensch(mitarbeiter)
                    || !geprueft.add(mitarbeiter.getId())) continue;
            pruefeMitarbeiter(mitarbeiter.getId(), heute, jetzt);
        }
    }

    private void pruefeMitarbeiter(Long mitarbeiterId, LocalDate heute, LocalDateTime jetzt) {
        Tagesbilanz bilanz = Tagesbilanz.aus(
                zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(mitarbeiterId, heute.atStartOfDay(), jetzt),
                jetzt);
        for (Stufe stufe : STUFEN) {
            if (bilanz.arbeit().compareTo(stufe.arbeit().minus(VORLAUF)) < 0) continue;
            // Nur die höchste erreichte Grenze zählt. Fehlt dort Pause, ist das die
            // Erinnerung – egal ob schon verschickt; reicht sie, reicht sie auch darunter.
            if (bilanz.pause().compareTo(stufe.pause()) < 0) {
                merkeUndSende(mitarbeiterId, heute, stufe.art(), jetzt, "Zeit für eine Pause",
                        gesamtPausenText(stufe, bilanz), URL_ZEITERFASSUNG, TYP_PAUSE);
                return;
            }
            break;
        }
        if (bilanz.amStueck().compareTo(MAX_AM_STUECK.minus(VORLAUF)) >= 0) {
            merkeUndSende(mitarbeiterId, heute, ZeitHinweisGesendet.Art.PAUSE_AM_STUECK, jetzt, "Zeit für eine Pause",
                    String.format("Du arbeitest seit %s Std. ohne Pause. Länger als 6 Stunden am Stück ist laut "
                            + "Arbeitszeitgesetz nicht erlaubt – mach bitte mindestens 15 Minuten Pause.",
                            stunden(bilanz.amStueck())),
                    URL_ZEITERFASSUNG, TYP_PAUSE);
        }
    }

    private void merkeUndSende(Long mitarbeiterId, LocalDate tag, ZeitHinweisGesendet.Art art, LocalDateTime jetzt,
            String titel, String text, String url, String typ) {
        if (gesendetRepository.existsByMitarbeiterIdAndTagAndArt(mitarbeiterId, tag, art)) return;
        gesendetRepository.save(new ZeitHinweisGesendet(mitarbeiterId, tag, art, jetzt));
        nachCommit(() -> webPushService.notifyMitarbeiter(mitarbeiterId, titel, text, url, typ));
        log.info("Zeit-Hinweis {} für Mitarbeiter {} vorgemerkt", art, mitarbeiterId);
    }

    private static boolean istAktiverMensch(Mitarbeiter mitarbeiter) {
        return mitarbeiter.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(mitarbeiter.getAktiv());
    }

    private static String gesamtPausenText(Stufe stufe, Tagesbilanz bilanz) {
        long fehlt = stufe.pause().minus(bilanz.pause()).toMinutes();
        return String.format(
                "Du arbeitest heute schon %s Std. Ab %d Stunden sind laut Arbeitszeitgesetz insgesamt "
                        + "%d Minuten Pause Pflicht – dir fehlen noch %d Minuten.",
                stunden(bilanz.arbeit()), stufe.arbeit().toHours(), stufe.pause().toMinutes(), fehlt);
    }

    private static String stunden(Duration dauer) {
        return String.format("%d:%02d", dauer.toHours(), dauer.toMinutesPart());
    }

    /** Erst nach dem Commit senden: Ein zurückgerollter Lauf soll keinen Push hinterlassen. */
    private static void nachCommit(Runnable senden) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Ein Push-Fehler darf den Prüf-Job nie zum Absturz bringen.
                    try {
                        senden.run();
                    } catch (RuntimeException e) {
                        log.warn("Zeit-Hinweis per Push fehlgeschlagen: {}", e.getMessage());
                    }
                }
            });
        } else {
            senden.run();
        }
    }
}
