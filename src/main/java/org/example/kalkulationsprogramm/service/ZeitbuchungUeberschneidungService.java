package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Handy gewinnt" bei Überschneidungen.
 *
 * <p>Ohne Netz stempelt das Handy weiter; die Buchungen kommen später mit ihrer echten
 * Uhrzeit an. Stand für diese Zeit schon etwas im Büro (am PC nachgetragen, im Büro
 * gestartet), überlappen sich die Zeiten. Dann hat das Handy Vorrang: Der Büro-Eintrag
 * wird gekürzt, geteilt oder – wenn er ganz in der Handy-Zeit liegt – storniert. Jede
 * Änderung steht im GoBD-Protokoll ({@link ZeitbuchungAuditService}). Dazu entsteht eine
 * Meldung je Büro-Eintrag ({@link ZeitbuchungUeberschneidung}) für die Glocke am PC und
 * die Nachrichten am Handy.
 *
 * <p>"Büro-Eintrag" heißt: nicht vom Handy erfasst ({@link ErfassungsQuelle#MOBILE_APP}).
 * Eigene Handy-Buchungen und vom Auto-Stop beendete Buchungen werden hier nie angefasst.
 * Liegt ein Büro-Eintrag in einem festgeschriebenen Monat, bleibt er unverändert – die
 * Meldung bittet dann ums Klären im Büro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZeitbuchungUeberschneidungService {

    /**
     * Beginnt ein Büro-Eintrag höchstens so viel vor der Handy-Buchung, zählt er als
     * "beginnt mit ihr" (wird also nach hinten geschoben statt in Splitter zerteilt).
     * Fängt Uhr-Abweichungen und den abgeteilten Rest beim direkten Wechsel ab.
     */
    static final Duration START_TOLERANZ = Duration.ofMinutes(1);

    /** So weit reicht eine gerade angestochene Handy-Buchung fürs Erste (Ende noch offen). */
    static final Duration NEUE_BUCHUNG_VORLAUF = Duration.ofMinutes(1);

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitbuchungUeberschneidungRepository ueberschneidungRepository;
    private final ZeitbuchungAuditService auditService;
    private final MonatsSaldoService monatsSaldoService;
    private final ApplicationEventPublisher eventPublisher;

    /** Nach dem Commit: Push ans Handy des Mitarbeiters (siehe {@code UeberschneidungPushListener}). */
    public record UeberschneidungGemeldet(Long mitarbeiterId, String titel, String text) {
    }

    /** Was in den Glocken angezeigt wird. */
    public record Anzeige(String titel, String text) {
    }

    /** Ist die Buchung im Büro entstanden (und darf damit vom Handy überstimmt werden)? */
    public static boolean istBueroEintrag(Zeitbuchung buchung) {
        return buchung.getErfasstVia() != null && buchung.getErfasstVia() != ErfassungsQuelle.MOBILE_APP;
    }

    // ═══════════════════════════════════════════════════════════════
    // AUFLÖSEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Eine Handy-Buchung ist abgeschlossen (Feierabend, Pause, Wechsel): Büro-Einträge, die
     * ihren Zeitraum überlappen, weichen.
     */
    @Transactional
    public List<ZeitbuchungUeberschneidung> nachHandyBuchung(Zeitbuchung handyBuchung, Mitarbeiter mitarbeiter) {
        if (handyBuchung.getStartZeit() == null || handyBuchung.getEndeZeit() == null) {
            return List.of();
        }
        // Meldungen vom Anstechen kannten das Ende noch nicht ("ab 07:00") – jetzt nachtragen,
        // auch wenn ihr Büro-Eintrag schon entfernt ist und unten nicht mehr auftaucht.
        if (handyBuchung.getId() != null) {
            for (ZeitbuchungUeberschneidung meldung : ueberschneidungRepository.findByHandyBuchungId(
                    handyBuchung.getId())) {
                if (meldung.getHandyBis() == null || handyBuchung.getEndeZeit().isAfter(meldung.getHandyBis())) {
                    meldung.setHandyBis(handyBuchung.getEndeZeit());
                    ueberschneidungRepository.save(meldung);
                }
            }
        }
        return aufloesen(mitarbeiter, handyBuchung.getId(), handyBuchung.getStartZeit(), handyBuchung.getEndeZeit(),
                false);
    }

    /**
     * Vor dem Speichern einer neuen Handy-Buchung (Anstechen, Pause): Büro-Einträge, die zu
     * dieser Zeit noch laufen, werden beendet bzw. entfernt; einer, der genau dann beginnt,
     * rückt nach hinten. Den Rest regelt {@link #nachHandyBuchung}, sobald das Ende feststeht.
     * Muss VOR dem Speichern laufen – es darf je Mitarbeiter nur eine offene Buchung geben.
     */
    @Transactional
    public List<ZeitbuchungUeberschneidung> vorNeuerHandyBuchung(Mitarbeiter mitarbeiter, LocalDateTime start) {
        return aufloesen(mitarbeiter, null, start, start.plus(NEUE_BUCHUNG_VORLAUF), true);
    }

    /** Trägt die inzwischen gespeicherte Handy-Buchung in Meldungen aus {@link #vorNeuerHandyBuchung} nach. */
    @Transactional
    public void handyBuchungZuordnen(Collection<ZeitbuchungUeberschneidung> meldungen, Long handyBuchungId) {
        for (ZeitbuchungUeberschneidung meldung : meldungen) {
            if (meldung.getHandyBuchungId() == null) {
                meldung.setHandyBuchungId(handyBuchungId);
                ueberschneidungRepository.save(meldung);
            }
        }
    }

    /**
     * @param auchSpaeterGestartete auch im Büro gestartete Buchungen, die NACH {@code von} begonnen
     *                              haben und noch laufen (die neue Handy-Buchung läuft seit
     *                              {@code von} und deckt sie ab – und es darf nur eine laufen)
     */
    private List<ZeitbuchungUeberschneidung> aufloesen(Mitarbeiter mitarbeiter, Long handyBuchungId,
            LocalDateTime von, LocalDateTime bis, boolean auchSpaeterGestartete) {
        if (mitarbeiter == null || mitarbeiter.getId() == null || !bis.isAfter(von)) {
            return List.of();
        }
        Stream<Zeitbuchung> kandidaten = zeitbuchungRepository.findUeberlappende(mitarbeiter.getId(), von, bis)
                .stream();
        if (auchSpaeterGestartete) {
            kandidaten = Stream.concat(kandidaten,
                    zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(mitarbeiter.getId()).stream());
        }
        List<Zeitbuchung> bueroEintraege = kandidaten
                .distinct()
                .filter(z -> handyBuchungId == null || !handyBuchungId.equals(z.getId()))
                .filter(ZeitbuchungUeberschneidungService::istBueroEintrag)
                .toList();
        // Beim Anstechen ist das Ende der Handy-Buchung noch offen: In der Meldung steht dann
        // "ab 07:00", nicht der technische Vorlauf von einer Minute.
        LocalDateTime handyBis = auchSpaeterGestartete ? null : bis;
        List<ZeitbuchungUeberschneidung> meldungen = new ArrayList<>();
        for (Zeitbuchung eintrag : bueroEintraege) {
            meldungen.add(weichen(eintrag, mitarbeiter, handyBuchungId, von, bis, handyBis));
        }
        return meldungen;
    }

    /** Lässt einen Büro-Eintrag der Handy-Zeit [von, bis) weichen und schreibt die Meldung. */
    private ZeitbuchungUeberschneidung weichen(Zeitbuchung eintrag, Mitarbeiter mitarbeiter, Long handyBuchungId,
            LocalDateTime von, LocalDateTime bis, LocalDateTime handyBis) {
        Optional<ZeitbuchungUeberschneidung> vorhanden = ueberschneidungRepository.findZuBuchung(eintrag.getId())
                .stream().findFirst();
        ZeitbuchungUeberschneidung meldung = vorhanden.orElseGet(() -> neueMeldung(eintrag, mitarbeiter));
        LocalDateTime jetzt = LocalDateTime.now();

        UeberschneidungErgebnis schritt = festgeschrieben(eintrag)
                ? UeberschneidungErgebnis.NICHT_GEAENDERT
                : anpassen(eintrag, mitarbeiter, meldung, von, bis);

        meldung.setErgebnis(gesamtErgebnis(meldung, schritt, vorhanden.isEmpty()));
        meldung.setHandyVon(meldung.getHandyVon() == null || von.isBefore(meldung.getHandyVon())
                ? von : meldung.getHandyVon());
        if (handyBis != null && (meldung.getHandyBis() == null || handyBis.isAfter(meldung.getHandyBis()))) {
            meldung.setHandyBis(handyBis);
        }
        if (meldung.getHandyBuchungId() == null) {
            meldung.setHandyBuchungId(handyBuchungId);
        }
        meldung.setGeaendertAm(jetzt);
        // Neuer Stand → am Handy wieder als ungelesen zeigen.
        meldung.setMitarbeiterGesehenAm(null);
        ZeitbuchungUeberschneidung gespeichert = ueberschneidungRepository.save(meldung);

        log.info("Überschneidung: Büro-Eintrag {} von Mitarbeiter {} -> {} (Handy {} - {})",
                eintrag.getId(), mitarbeiter.getId(), schritt, von, bis);
        if (vorhanden.isEmpty()) {
            Anzeige anzeige = anzeigeFuerMitarbeiter(gespeichert);
            eventPublisher.publishEvent(new UeberschneidungGemeldet(mitarbeiter.getId(), anzeige.titel(),
                    anzeige.text()));
        }
        return gespeichert;
    }

    private ZeitbuchungUeberschneidung neueMeldung(Zeitbuchung eintrag, Mitarbeiter mitarbeiter) {
        ZeitbuchungUeberschneidung meldung = new ZeitbuchungUeberschneidung();
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBueroBuchungId(eintrag.getId());
        meldung.setBueroVon(eintrag.getStartZeit());
        meldung.setBueroBis(eintrag.getEndeZeit());
        meldung.setBueroBezeichnung(bezeichnung(eintrag));
        meldung.setErstelltAm(LocalDateTime.now());
        return meldung;
    }

    /**
     * Passt den Eintrag an. Fälle (s = Büro-Eintrag, [von, bis) = Handy):
     * läuft noch → beenden bzw. entfernen; liegt ganz drin → entfernen; umschließt → teilen;
     * ragt vorne hinein → Ende vorziehen; ragt hinten hinaus → Start nach hinten schieben.
     */
    private UeberschneidungErgebnis anpassen(Zeitbuchung eintrag, Mitarbeiter mitarbeiter,
            ZeitbuchungUeberschneidung meldung, LocalDateTime von, LocalDateTime bis) {
        LocalDateTime start = eintrag.getStartZeit();
        LocalDateTime ende = eintrag.getEndeZeit();
        boolean beginntDavor = start.isBefore(von.minus(START_TOLERANZ));
        String grund = "Überschneidung mit Handy-Buchung " + zeitraum(von, bis) + " - das Handy hat Vorrang";

        if (ende == null) {
            if (beginntDavor) {
                setzeEnde(eintrag, von, mitarbeiter, grund);
                return UeberschneidungErgebnis.BEENDET;
            }
            entfernen(eintrag, mitarbeiter, grund);
            return UeberschneidungErgebnis.ENTFERNT;
        }
        if (!beginntDavor && !ende.isAfter(bis)) {
            entfernen(eintrag, mitarbeiter, grund);
            return UeberschneidungErgebnis.ENTFERNT;
        }
        if (beginntDavor && ende.isAfter(bis)) {
            Long mitarbeiterId = mitarbeiter.getId();
            boolean restPasst = !zeitbuchungRepository.existsByMitarbeiterIdAndStartZeit(mitarbeiterId, bis);
            if (restPasst) {
                Zeitbuchung rest = restAnlegen(eintrag, bis, ende, mitarbeiter, grund);
                meldung.setRestBuchungId(rest.getId());
            }
            setzeEnde(eintrag, von, mitarbeiter, grund);
            return restPasst ? UeberschneidungErgebnis.GETEILT : UeberschneidungErgebnis.GEKUERZT;
        }
        if (beginntDavor) {
            setzeEnde(eintrag, von, mitarbeiter, grund);
            return UeberschneidungErgebnis.GEKUERZT;
        }
        // Beginnt mit/in der Handy-Zeit und reicht darüber hinaus: Start nach hinten.
        if (zeitbuchungRepository.existsByMitarbeiterIdAndStartZeit(mitarbeiter.getId(), bis)) {
            return UeberschneidungErgebnis.ZEIT_BELEGT;
        }
        setzeStart(eintrag, bis, mitarbeiter, grund);
        return UeberschneidungErgebnis.GEKUERZT;
    }

    /** Ergebnis der ganzen Meldung aus dem aktuellen Stand von Eintrag und abgeteiltem Rest. */
    private UeberschneidungErgebnis gesamtErgebnis(ZeitbuchungUeberschneidung meldung,
            UeberschneidungErgebnis schritt, boolean neu) {
        if (schritt == UeberschneidungErgebnis.NICHT_GEAENDERT || schritt == UeberschneidungErgebnis.ZEIT_BELEGT) {
            return neu ? schritt : meldung.getErgebnis();
        }
        boolean eintragDa = meldung.getBueroBuchungId() != null
                && zeitbuchungRepository.existsById(meldung.getBueroBuchungId());
        boolean restDa = meldung.getRestBuchungId() != null
                && zeitbuchungRepository.existsById(meldung.getRestBuchungId());
        if (!eintragDa && !restDa) {
            return UeberschneidungErgebnis.ENTFERNT;
        }
        if (eintragDa && restDa) {
            return UeberschneidungErgebnis.GETEILT;
        }
        if (!neu && meldung.getErgebnis() == UeberschneidungErgebnis.BEENDET) {
            return UeberschneidungErgebnis.BEENDET;
        }
        return schritt == UeberschneidungErgebnis.BEENDET ? schritt : UeberschneidungErgebnis.GEKUERZT;
    }

    /** Berührt der Eintrag einen festgeschriebenen Monat (Start oder Ende, z. B. über den Monatswechsel)? */
    private boolean festgeschrieben(Zeitbuchung eintrag) {
        Long mitarbeiterId = eintrag.getMitarbeiter().getId();
        return Stream.of(eintrag.getStartZeit(), eintrag.getEndeZeit())
                .filter(java.util.Objects::nonNull)
                .anyMatch(zeit -> monatsSaldoService.isMonatFestgeschrieben(mitarbeiterId, zeit.getYear(),
                        zeit.getMonthValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    // ÄNDERUNGEN MIT GoBD-PROTOKOLL
    // ═══════════════════════════════════════════════════════════════

    private void setzeEnde(Zeitbuchung eintrag, LocalDateTime ende, Mitarbeiter bearbeiter, String grund) {
        eintrag.setEndeZeit(ende);
        eintrag.setAnzahlInStunden(stunden(eintrag));
        speichereAenderung(eintrag, bearbeiter, grund);
    }

    private void setzeStart(Zeitbuchung eintrag, LocalDateTime start, Mitarbeiter bearbeiter, String grund) {
        LocalDateTime alterStart = eintrag.getStartZeit();
        eintrag.setStartZeit(start);
        eintrag.setAnzahlInStunden(stunden(eintrag));
        speichereAenderung(eintrag, bearbeiter, grund);
        // Liegt der neue Start in einem anderen Monat, ist auch der alte Monat betroffen.
        monatsSaldoService.invalidiereFuerDateTime(eintrag.getMitarbeiter().getId(), alterStart);
    }

    private void speichereAenderung(Zeitbuchung eintrag, Mitarbeiter bearbeiter, String grund) {
        // Erst Version erhöhen, dann Audit protokollieren (sonst Unique-Constraint, siehe Stop).
        eintrag.markiereAlsGeaendert(bearbeiter);
        auditService.protokolliereAenderung(eintrag, bearbeiter, ErfassungsQuelle.SYSTEM, grund);
        // Sofort schreiben: Die neue Handy-Buchung folgt im selben Ablauf, und die
        // Unique-Indizes (Start je Mitarbeiter, eine offene Buchung) müssen dann schon passen.
        zeitbuchungRepository.saveAndFlush(eintrag);
        monatsSaldoService.invalidiereFuerDateTime(eintrag.getMitarbeiter().getId(), eintrag.getStartZeit());
    }

    /** Storniert wie "Löschen" am PC: Storno-Protokoll mit vollem Stand, dann weg. */
    private void entfernen(Zeitbuchung eintrag, Mitarbeiter bearbeiter, String grund) {
        Long mitarbeiterId = eintrag.getMitarbeiter().getId();
        LocalDateTime start = eintrag.getStartZeit();
        eintrag.markiereAlsGeaendert(bearbeiter);
        zeitbuchungRepository.save(eintrag);
        auditService.protokolliereStorno(eintrag, bearbeiter, ErfassungsQuelle.SYSTEM, grund);
        zeitbuchungRepository.delete(eintrag);
        zeitbuchungRepository.flush();
        monatsSaldoService.invalidiereFuerDateTime(mitarbeiterId, start);
    }

    /** Der Teil eines geteilten Büro-Eintrags nach der Handy-Buchung – sonst unverändert. */
    private Zeitbuchung restAnlegen(Zeitbuchung eintrag, LocalDateTime start, LocalDateTime ende,
            Mitarbeiter bearbeiter, String grund) {
        Zeitbuchung rest = new Zeitbuchung();
        rest.setMitarbeiter(eintrag.getMitarbeiter());
        rest.setProjekt(eintrag.getProjekt());
        rest.setArbeitsgang(eintrag.getArbeitsgang());
        rest.setArbeitsgangStundensatz(eintrag.getArbeitsgangStundensatz());
        rest.setProjektProduktkategorie(eintrag.getProjektProduktkategorie());
        rest.setTyp(eintrag.getTyp());
        rest.setNotiz(eintrag.getNotiz());
        rest.setStartZeit(start);
        rest.setEndeZeit(ende);
        rest.setAnzahlInStunden(stunden(rest));
        rest.setErfasstVon(eintrag.getErfasstVon());
        rest.setErfasstVia(eintrag.getErfasstVia());
        rest.setErfasstAm(LocalDateTime.now());
        rest.setVersion(1);
        Zeitbuchung gespeichert = zeitbuchungRepository.saveAndFlush(rest);
        auditService.protokolliereErstellung(gespeichert, bearbeiter, ErfassungsQuelle.SYSTEM);
        log.info("Büro-Eintrag {} geteilt, Rest {} ({}): {}", eintrag.getId(), gespeichert.getId(),
                zeitraum(start, ende), grund);
        return gespeichert;
    }

    private static BigDecimal stunden(Zeitbuchung buchung) {
        long minuten = Duration.between(buchung.getStartZeit(), buchung.getEndeZeit()).toMinutes();
        return BigDecimal.valueOf(Math.max(0, minuten)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    // ═══════════════════════════════════════════════════════════════
    // MELDUNGEN LESEN
    // ═══════════════════════════════════════════════════════════════

    /** Für die Glocke am PC: alle Meldungen der letzten 30 Tage, zuletzt geänderte zuerst. */
    @Transactional(readOnly = true)
    public List<ZeitbuchungUeberschneidung> fuerBuero() {
        return ueberschneidungRepository.findGeaendertAb(LocalDateTime.now().minusDays(30));
    }

    /** Für die Nachrichten am Handy: ungelesene Meldungen des Mitarbeiters. */
    @Transactional(readOnly = true)
    public List<ZeitbuchungUeberschneidung> ungeleseneFuerMitarbeiter(Long mitarbeiterId) {
        return ueberschneidungRepository.findByMitarbeiterIdAndMitarbeiterGesehenAmIsNullOrderByGeaendertAmDesc(
                mitarbeiterId);
    }

    /** "Gelesen" am Handy – nur die eigene Meldung. */
    @Transactional
    public boolean alsGesehenMarkieren(Long id, Long mitarbeiterId) {
        return ueberschneidungRepository.findByIdAndMitarbeiterId(id, mitarbeiterId)
                .map(meldung -> {
                    meldung.setMitarbeiterGesehenAm(LocalDateTime.now());
                    ueberschneidungRepository.save(meldung);
                    return true;
                })
                .orElse(false);
    }

    /** Text für die Glocke am PC ("Max Mustermann am 28.10.2026"). */
    @Transactional(readOnly = true)
    public Anzeige anzeigeFuerBuero(ZeitbuchungUeberschneidung meldung) {
        Mitarbeiter ma = meldung.getMitarbeiter();
        String name = ma != null ? ma.getVorname() + " " + ma.getNachname() : "Mitarbeiter";
        return new Anzeige(name + " am " + meldung.getBueroVon().format(TAG),
                "Handy-Buchung " + zeitraum(meldung.getHandyVon(), meldung.getHandyBis()) + " hatte Vorrang. "
                        + bueroText(meldung));
    }

    /** Text für das Handy des Mitarbeiters. */
    @Transactional(readOnly = true)
    public Anzeige anzeigeFuerMitarbeiter(ZeitbuchungUeberschneidung meldung) {
        return new Anzeige("Überschneidung am " + meldung.getBueroVon().format(TAG),
                "Deine Handy-Buchung " + zeitraum(meldung.getHandyVon(), meldung.getHandyBis())
                        + " hatte Vorrang. " + bueroText(meldung));
    }

    /** "Büro-Eintrag „Musterhaus · Montage“ 07:00–16:00 → jetzt 07:00–10:00 und 12:00–16:00." */
    private String bueroText(ZeitbuchungUeberschneidung meldung) {
        String eintrag = "Büro-Eintrag"
                + (meldung.getBueroBezeichnung() != null ? " „" + meldung.getBueroBezeichnung() + "“" : "")
                + " " + zeitraum(meldung.getBueroVon(), meldung.getBueroBis());
        if (meldung.getErgebnis() == UeberschneidungErgebnis.NICHT_GEAENDERT) {
            return eintrag + " wurde nicht geändert, weil der Monat schon abgeschlossen ist. "
                    + "Bitte im Büro klären.";
        }
        if (meldung.getErgebnis() == UeberschneidungErgebnis.ZEIT_BELEGT) {
            return eintrag + " wurde nicht geändert, weil danach schon ein anderer Eintrag beginnt. "
                    + "Bitte im Büro klären.";
        }
        List<String> jetzt = Stream.of(meldung.getBueroBuchungId(), meldung.getRestBuchungId())
                .filter(java.util.Objects::nonNull)
                .map(zeitbuchungRepository::findById)
                .flatMap(Optional::stream)
                .map(z -> zeitraum(z.getStartZeit(), z.getEndeZeit()))
                .collect(Collectors.toList());
        if (jetzt.isEmpty()) {
            return eintrag + " wurde entfernt.";
        }
        return eintrag + " → jetzt " + String.join(" und ", jetzt) + ".";
    }

    private static String bezeichnung(Zeitbuchung eintrag) {
        if (eintrag.getTyp() == BuchungsTyp.PAUSE) {
            return "Pause";
        }
        List<String> teile = new ArrayList<>();
        if (eintrag.getProjekt() != null && eintrag.getProjekt().getBauvorhaben() != null) {
            teile.add(eintrag.getProjekt().getBauvorhaben());
        }
        if (eintrag.getArbeitsgang() != null && eintrag.getArbeitsgang().getBeschreibung() != null) {
            teile.add(eintrag.getArbeitsgang().getBeschreibung());
        }
        String text = String.join(" · ", teile);
        if (text.isBlank()) {
            return null;
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    /** "07:00–16:00"; über Mitternacht mit Datum, noch offen: "ab 07:00". */
    static String zeitraum(LocalDateTime von, LocalDateTime bis) {
        if (von == null) {
            return "";
        }
        if (bis == null) {
            return "ab " + von.format(UHRZEIT);
        }
        String ende = von.toLocalDate().equals(bis.toLocalDate())
                ? bis.format(UHRZEIT)
                : bis.format(TAG) + " " + bis.format(UHRZEIT);
        return von.format(UHRZEIT) + "–" + ende;
    }
}
