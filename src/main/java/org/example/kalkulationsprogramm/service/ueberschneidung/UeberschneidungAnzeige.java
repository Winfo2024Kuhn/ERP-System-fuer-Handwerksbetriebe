package org.example.kalkulationsprogramm.service.ueberschneidung;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;

/**
 * Texte der Überschneidungs-Meldungen ("Handy gewinnt") für die Glocke am PC und die
 * Nachrichten am Handy. Reine Funktionen ohne Spring und Datenbank: Alles, was gebraucht wird,
 * steht in der Meldung und im gesammelt geladenen {@link UeberschneidungStand}.
 *
 * <p>Aufbau: "Vom Handy: Arbeit 11:27–12:00, Pause 12:00–13:34. Büro-Eintrag „Pause“
 * 12:00–13:00 wurde deshalb entfernt." Jede Handy-Buchung steht einzeln (höchstens
 * {@value #MAX_HANDY_ZEITEN}, dann "und 2 weitere"). Altmeldungen ohne festgehaltene Zeiten
 * nennen die gemerkte Spanne ("Vom Handy: 11:27–13:34.").
 */
public final class UeberschneidungAnzeige {

    /** So viele Handy-Zeiten nennt eine Meldung, der Rest heißt "und 2 weitere". */
    static final int MAX_HANDY_ZEITEN = 3;

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");

    /** Was in den Glocken angezeigt wird. */
    public record Anzeige(String titel, String text) {
    }

    private UeberschneidungAnzeige() {
    }

    /** Glocke am PC: "Max Mustermann am 18.11.2026", "Vom Handy: …". */
    public static Anzeige fuerBuero(ZeitbuchungUeberschneidung meldung, UeberschneidungStand stand) {
        Mitarbeiter ma = meldung.getMitarbeiter();
        String name = ma != null ? ma.getVorname() + " " + ma.getNachname() : "Mitarbeiter";
        return new Anzeige(name + " am " + meldung.getBueroVon().format(TAG),
                "Vom Handy: " + handyText(meldung, stand) + ". " + bueroText(meldung, stand));
    }

    /** Handy des Mitarbeiters: "Überschneidung am 18.11.2026", "Deine Handy-Zeiten: …". */
    public static Anzeige fuerMitarbeiter(ZeitbuchungUeberschneidung meldung, UeberschneidungStand stand) {
        return new Anzeige("Überschneidung am " + meldung.getBueroVon().format(TAG),
                "Deine Handy-Zeiten: " + handyText(meldung, stand) + ". " + bueroText(meldung, stand));
    }

    /** "Arbeit 11:27–12:00, Pause 12:00–13:34" bzw. bei Altmeldungen nur die Spanne. */
    private static String handyText(ZeitbuchungUeberschneidung meldung, UeberschneidungStand stand) {
        List<ZeitbuchungUeberschneidungHandyZeit> zeiten = new ArrayList<>(stand.handyZeitenVon(meldung));
        if (zeiten.isEmpty()) {
            return zeitraum(meldung.getHandyVon(), meldung.getHandyBis());
        }
        zeiten.sort(Comparator.comparing(ZeitbuchungUeberschneidungHandyZeit::getVon));
        String genannt = zeiten.stream()
                .limit(MAX_HANDY_ZEITEN)
                .map(h -> art(h.getArt()) + " " + zeitraum(h.getVon(), h.getBis()))
                .collect(Collectors.joining(", "));
        int weitere = zeiten.size() - MAX_HANDY_ZEITEN;
        return weitere > 0 ? genannt + " und " + weitere + " weitere" : genannt;
    }

    private static String art(BuchungsTyp typ) {
        return typ == BuchungsTyp.PAUSE ? "Pause" : "Arbeit";
    }

    /** "Büro-Eintrag „Musterhaus · Montage“ 07:00–16:00 → jetzt 07:00–10:00 und 12:00–16:00." */
    private static String bueroText(ZeitbuchungUeberschneidung meldung, UeberschneidungStand stand) {
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
        List<String> jetzt = stand.bueroStand(meldung).stream()
                .map(z -> zeitraum(z.getStartZeit(), z.getEndeZeit()))
                .toList();
        if (jetzt.isEmpty()) {
            return eintrag + " wurde deshalb entfernt.";
        }
        return eintrag + " → jetzt " + String.join(" und ", jetzt) + ".";
    }

    /** "Projekt · Arbeitsgang" bzw. "Pause"; {@code null}, wenn nichts bekannt ist. Höchstens 500 Zeichen. */
    public static String bezeichnung(Zeitbuchung eintrag) {
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
    public static String zeitraum(LocalDateTime von, LocalDateTime bis) {
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
