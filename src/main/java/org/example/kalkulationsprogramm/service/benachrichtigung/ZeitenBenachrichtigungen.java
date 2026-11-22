package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungEintragDto;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.ZeitbuchungUeberschneidungService;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungAnzeige;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungStand;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Die beiden Zeiterfassungs-Quellen der Glocke („Zeiten prüfen“): automatisch beendete
 * Buchungen („Zeiten ohne Feierabend“) und Überschneidungen Handy/Büro. Jeder Eintrag führt
 * über {@link ZeitkalenderLink} direkt zum Tag im Zeitkalender und markiert die betroffenen
 * Buchungen. Läuft in der Transaktion, die {@link BenachrichtigungService} je Quelle öffnet.
 */
@Component
@RequiredArgsConstructor
public class ZeitenBenachrichtigungen {

    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UHRZEIT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /** Meldungen der letzten 30 Tage, wie die Überschneidungen. */
    private static final int TAGE_ZURUECK = 30;
    private static final int ANZEIGE_LIMIT = 5;

    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitbuchungUeberschneidungService ueberschneidungService;

    /**
     * Automatisch beendete Zeitbuchungen (letzte 30 Tage). Der Auto-Stop hat eine geschätzte
     * Endezeit eingetragen, weil der Mitarbeiter nicht abgestochen hat; diese Stunden fließen
     * ungeprüft in den Monatssaldo. Der Auto-Stop verdrängt nichts – überlappt die geschätzte
     * Zeit andere Einträge, zählen Stunden doppelt. Das sagt der Text dazu. Die Überlappungen
     * kommen für alle Einträge aus einer Sammelabfrage.
     */
    BenachrichtigungService.Quelle<?> automatischBeendet() {
        List<Zeitbuchung> autoBeendet = zeitbuchungRepository
                .findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(
                        LocalDateTime.now().minusDays(TAGE_ZURUECK));
        List<Zeitbuchung> kandidaten = ueberlappungsKandidaten(autoBeendet);
        return BenachrichtigungService.Quelle.von("ZEITEN_AUTO_BEENDET", "Zeiten ohne Feierabend", "Clock",
                "/zeitbuchungen", autoBeendet, z -> "ZEIT_AUTO_BEENDET:" + z.getId(),
                z -> autoBeendetEintrag(z, ueberlappende(z, kandidaten)), ANZEIGE_LIMIT);
    }

    private static BenachrichtigungEintragDto autoBeendetEintrag(Zeitbuchung z, List<Zeitbuchung> ueberlappend) {
        Mitarbeiter ma = z.getMitarbeiter();
        String bis = z.getEndeZeit() != null ? z.getEndeZeit().format(UHRZEIT_FORMAT) : "?";
        long andere = ueberlappend.stream().filter(o -> !o.getId().equals(z.getId())).count();
        String text = "Kein Feierabend gestempelt, automatisch auf " + bis + " Uhr gesetzt."
                + (andere == 1 ? " Überschneidet sich mit einem anderen Eintrag." : "")
                + (andere > 1 ? " Überschneidet sich mit " + andere + " anderen Einträgen." : "");
        List<Zeitbuchung> markiert = ueberlappend.isEmpty() ? List.of(z) : ueberlappend;
        return new BenachrichtigungEintragDto("ZEIT_AUTO_BEENDET:" + z.getId(), "ZEIT_AUTO_BEENDET",
                name(ma) + " am " + z.getStartZeit().format(TAG_FORMAT), text, z.getStartZeit().toString(),
                ZeitkalenderLink.zumTag(ma != null ? ma.getId() : null, z.getStartZeit().toLocalDate(),
                        ZeitkalenderLink.ANLASS_OHNE_FEIERABEND, markiert));
    }

    /**
     * Eine Abfrage für alle Einträge: Buchungen der betroffenen Mitarbeiter, die irgendeinen
     * Teil von [frühester Start, spätestes Ende] berühren. Gefiltert wird danach je Buchung.
     */
    private List<Zeitbuchung> ueberlappungsKandidaten(List<Zeitbuchung> autoBeendet) {
        List<Zeitbuchung> pruefbar = autoBeendet.stream().filter(ZeitenBenachrichtigungen::pruefbar).toList();
        if (pruefbar.isEmpty()) {
            return List.of();
        }
        List<Long> mitarbeiterIds = pruefbar.stream().map(z -> z.getMitarbeiter().getId()).distinct().toList();
        LocalDateTime von = pruefbar.stream().map(Zeitbuchung::getStartZeit).min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime bis = pruefbar.stream().map(Zeitbuchung::getEndeZeit).max(Comparator.naturalOrder())
                .orElseThrow();
        return zeitbuchungRepository.findUeberlappendeVonMitarbeitern(mitarbeiterIds, von, bis);
    }

    /** Buchungen desselben Mitarbeiters, die [start, ende) der Buchung überlappen – sie selbst eingeschlossen. */
    private static List<Zeitbuchung> ueberlappende(Zeitbuchung z, List<Zeitbuchung> kandidaten) {
        if (!pruefbar(z)) {
            return List.of();
        }
        Long mitarbeiterId = z.getMitarbeiter().getId();
        return kandidaten.stream()
                .filter(o -> o.getMitarbeiter() != null && mitarbeiterId.equals(o.getMitarbeiter().getId()))
                .filter(o -> o.getStartZeit().isBefore(z.getEndeZeit())
                        && (o.getEndeZeit() == null || o.getEndeZeit().isAfter(z.getStartZeit())))
                .toList();
    }

    private static boolean pruefbar(Zeitbuchung z) {
        return z.getMitarbeiter() != null && z.getMitarbeiter().getId() != null && z.getStartZeit() != null
                && z.getEndeZeit() != null;
    }

    /**
     * Überschneidungen: Eine (oft offline gestempelte) Handy-Buchung hatte Vorrang vor einem
     * Eintrag im Büro, der deshalb gekürzt, geteilt oder entfernt wurde – das Büro soll es
     * wissen. Handy-Zeiten und vorhandene Buchungen kommen für die ganze Liste auf einmal.
     */
    BenachrichtigungService.Quelle<?> ueberschneidungen() {
        List<ZeitbuchungUeberschneidung> meldungen = ueberschneidungService.fuerBuero();
        UeberschneidungStand stand = meldungen.isEmpty() ? UeberschneidungStand.LEER
                : ueberschneidungService.stand(meldungen);
        return BenachrichtigungService.Quelle.von("ZEITEN_UEBERSCHNEIDUNG", "Überschneidungen Handy/Büro",
                "CalendarClock", "/zeitbuchungen", meldungen, u -> "ZEIT_UEBERSCHNEIDUNG:" + u.getId(),
                u -> ueberschneidungEintrag(u, stand), ANZEIGE_LIMIT);
    }

    private static BenachrichtigungEintragDto ueberschneidungEintrag(ZeitbuchungUeberschneidung u,
            UeberschneidungStand stand) {
        UeberschneidungAnzeige.Anzeige anzeige = UeberschneidungAnzeige.fuerBuero(u, stand);
        Long mitarbeiterId = u.getMitarbeiter() != null ? u.getMitarbeiter().getId() : null;
        return new BenachrichtigungEintragDto("ZEIT_UEBERSCHNEIDUNG:" + u.getId(), "ZEIT_UEBERSCHNEIDUNG",
                anzeige.titel(), anzeige.text(), u.getGeaendertAm().toString(),
                ZeitkalenderLink.zumTag(mitarbeiterId, u.getBueroVon().toLocalDate(),
                        ZeitkalenderLink.ANLASS_UEBERSCHNEIDUNG, stand.betroffeneBuchungen(u)));
    }

    private static String name(Mitarbeiter ma) {
        return ma != null ? ma.getVorname() + " " + ma.getNachname() : "Mitarbeiter";
    }
}
