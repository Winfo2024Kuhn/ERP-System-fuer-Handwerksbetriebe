package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungRepository;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungAnzeige;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungHandyZeiten;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungStand;
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
 *
 * <p>Je Meldung werden die Handy-Buchungen festgehalten, die Vorrang hatten
 * ({@link ZeitbuchungUeberschneidungHandyZeit}), mit Art und Zeitraum so, wie das Handy sie
 * geschickt hat. Die Texte dazu baut {@link UeberschneidungAnzeige}.
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

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitbuchungUeberschneidungRepository ueberschneidungRepository;
    private final ZeitbuchungAuditService auditService;
    private final MonatsSaldoService monatsSaldoService;
    private final ApplicationEventPublisher eventPublisher;
    private final UeberschneidungHandyZeiten handyZeiten;

    /** Nach dem Commit: Push ans Handy des Mitarbeiters (siehe {@code UeberschneidungPushListener}). */
    public record UeberschneidungGemeldet(Long mitarbeiterId, String titel, String text) {
    }

    /** Ist die Buchung im Büro entstanden (und darf damit vom Handy überstimmt werden)? */
    public static boolean istBueroEintrag(Zeitbuchung buchung) {
        return buchung.getErfasstVia() != null && buchung.getErfasstVia() != ErfassungsQuelle.MOBILE_APP;
    }

    /**
     * Hat die Buchung nach {@code von} UND an einem späteren Kalendertag begonnen? Dann kam eine
     * Handy-Buchung ab {@code von} verspätet an (Offline-Stempel vom Vortag) – ein heute im Büro
     * gestarteter Eintrag weicht ihr nicht.
     */
    public static boolean laeuftSeitSpaeteremTag(Zeitbuchung buchung, LocalDateTime von) {
        LocalDateTime start = buchung.getStartZeit();
        return start != null && start.isAfter(von) && !start.toLocalDate().equals(von.toLocalDate());
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
            // Festgehaltene Handy-Zeiten aus dem Anstechen (auch in Meldungen, deren erste
            // Handy-Buchung eine andere war) bekommen ihr Ende. Neue Meldungen hält weichen() fest.
            handyZeiten.endeNachtragen(handyBuchung);
        }
        return aufloesen(mitarbeiter, handyBuchung, handyBuchung.getStartZeit(), handyBuchung.getEndeZeit(), false);
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

    /**
     * Trägt die inzwischen gespeicherte Handy-Buchung in Meldungen aus {@link #vorNeuerHandyBuchung}
     * nach und hält sie dort mit offenem Ende fest ("Arbeit ab 07:00"); das Ende kommt mit
     * {@link #nachHandyBuchung}.
     */
    @Transactional
    public void handyBuchungZuordnen(Collection<ZeitbuchungUeberschneidung> meldungen, Zeitbuchung handyBuchung) {
        for (ZeitbuchungUeberschneidung meldung : meldungen) {
            if (meldung.getHandyBuchungId() == null) {
                meldung.setHandyBuchungId(handyBuchung.getId());
                ueberschneidungRepository.save(meldung);
            }
            handyZeiten.festhalten(meldung, handyBuchung);
        }
    }

    /**
     * @param handyBuchung          die abgeschlossene Handy-Buchung, die festgehalten wird;
     *                              {@code null} beim Anstechen (noch nicht gespeichert)
     * @param auchSpaeterGestartete auch im Büro gestartete Buchungen, die NACH {@code von} begonnen
     *                              haben und noch laufen (die neue Handy-Buchung läuft seit
     *                              {@code von} und deckt sie ab – und es darf nur eine laufen).
     *                              Nur vom selben Kalendertag: Kommt ein Stempel vom Vortag erst
     *                              heute an, bleibt ein heute gestarteter Eintrag stehen
     *                              ({@link #laeuftSeitSpaeteremTag}).
     */
    private List<ZeitbuchungUeberschneidung> aufloesen(Mitarbeiter mitarbeiter, Zeitbuchung handyBuchung,
            LocalDateTime von, LocalDateTime bis, boolean auchSpaeterGestartete) {
        if (mitarbeiter == null || mitarbeiter.getId() == null || !bis.isAfter(von)) {
            return List.of();
        }
        Long handyBuchungId = handyBuchung != null ? handyBuchung.getId() : null;
        Stream<Zeitbuchung> kandidaten = zeitbuchungRepository.findUeberlappende(mitarbeiter.getId(), von, bis)
                .stream();
        if (auchSpaeterGestartete) {
            kandidaten = Stream.concat(kandidaten,
                    zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(mitarbeiter.getId()).stream()
                            .filter(z -> !laeuftSeitSpaeteremTag(z, von)));
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
            meldungen.add(weichen(eintrag, mitarbeiter, handyBuchung, von, bis, handyBis));
        }
        return meldungen;
    }

    /** Lässt einen Büro-Eintrag der Handy-Zeit [von, bis) weichen und schreibt die Meldung. */
    private ZeitbuchungUeberschneidung weichen(Zeitbuchung eintrag, Mitarbeiter mitarbeiter, Zeitbuchung handyBuchung,
            LocalDateTime von, LocalDateTime bis, LocalDateTime handyBis) {
        Long handyBuchungId = handyBuchung != null ? handyBuchung.getId() : null;
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
        if (handyBuchung != null) {
            handyZeiten.festhalten(gespeichert, handyBuchung);
        }

        log.info("Überschneidung: Büro-Eintrag {} von Mitarbeiter {} -> {} (Handy {} - {})",
                eintrag.getId(), mitarbeiter.getId(), schritt, von, bis);
        if (vorhanden.isEmpty()) {
            UeberschneidungAnzeige.Anzeige anzeige = UeberschneidungAnzeige.fuerMitarbeiter(gespeichert,
                    stand(List.of(gespeichert)));
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
        meldung.setBueroBezeichnung(UeberschneidungAnzeige.bezeichnung(eintrag));
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
        String grund = "Überschneidung mit Handy-Buchung " + UeberschneidungAnzeige.zeitraum(von, bis)
                + " - das Handy hat Vorrang";

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
                UeberschneidungAnzeige.zeitraum(start, ende), grund);
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

    /** Siehe {@link UeberschneidungHandyZeiten#stand}. */
    @Transactional(readOnly = true)
    public UeberschneidungStand stand(Collection<ZeitbuchungUeberschneidung> meldungen) {
        return handyZeiten.stand(meldungen);
    }

    /** Eine Meldung mit ihrem Text fürs Handy. */
    public record MeldungMitAnzeige(ZeitbuchungUeberschneidung meldung, UeberschneidungAnzeige.Anzeige anzeige) {
    }

    /** Für die Nachrichten am Handy: ungelesene Meldungen samt Text, Stand in zwei Abfragen für die Liste. */
    @Transactional(readOnly = true)
    public List<MeldungMitAnzeige> ungeleseneMitAnzeige(Long mitarbeiterId) {
        List<ZeitbuchungUeberschneidung> meldungen = ungeleseneFuerMitarbeiter(mitarbeiterId);
        UeberschneidungStand stand = stand(meldungen);
        return meldungen.stream()
                .map(m -> new MeldungMitAnzeige(m, UeberschneidungAnzeige.fuerMitarbeiter(m, stand)))
                .toList();
    }
}
