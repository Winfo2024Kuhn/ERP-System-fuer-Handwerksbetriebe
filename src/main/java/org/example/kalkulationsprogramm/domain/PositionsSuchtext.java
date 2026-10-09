package org.example.kalkulationsprogramm.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalisiert Positionstexte für die Suche – beim Speichern und beim Suchen
 * mit denselben Regeln, damit „Flachstahl 50 × 5“ und die Eingabe „50x5“
 * zueinander finden.
 *
 * <p>Regeln: klein, Malzeichen und Stern werden zu {@code x}, um ein {@code x}
 * zwischen zwei Ziffern steht kein Leerraum („50 x 5“ → „50x5“), Leerraum wird
 * zusammengefasst. Die Migration V405 befüllt vorhandene Zeilen mit denselben
 * Regeln in SQL.
 */
public final class PositionsSuchtext {

    /** Spaltenlänge von {@code lieferant_dokument_position.suchtext}. */
    public static final int MAX_LAENGE = 1000;
    /** Längere Eingaben schneiden wir ab – niemand tippt mehr. */
    public static final int MAX_EINGABE = 200;
    /** Mehr Suchwörter werden ignoriert. */
    public static final int MAX_WOERTER = 5;
    /** Kürzere Eingaben finden zu viel. */
    public static final int MIN_EINGABE = 2;
    /** Kürzere Chargen sind keine Chargen („1“, „A“) – sie verknüpfen nichts. */
    public static final int MIN_CHARGE = 5;

    private static final Pattern MALZEICHEN = Pattern.compile("[×*]");
    // Possessiv + Lookarounds: kein Backtracking, Ketten wie "2000 x 1000 x 3" ganz.
    // UNICODE_CHARACTER_CLASS: \s erkennt auch das geschützte Leerzeichen – wie
    // [[:space:]] im SQL-Backfill der Migration V405.
    private static final Pattern MASS_X = Pattern.compile("(?<=\\d)\\s*+x\\s*+(?=\\d)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LEERRAUM = Pattern.compile("\\s++", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NICHT_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]++");

    private PositionsSuchtext() {
    }

    /** Normalisiert einen Text; leer oder {@code null} → {@code null}. */
    public static String normalisiere(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.toLowerCase(Locale.ROOT);
        t = MALZEICHEN.matcher(t).replaceAll("x");
        t = MASS_X.matcher(t).replaceAll("x");
        t = LEERRAUM.matcher(t).replaceAll(" ").trim();
        return t.isEmpty() ? null : t;
    }

    /** Suchtext einer Position aus ihren Teilen; {@code null}, wenn alles leer ist. */
    public static String bilde(String... teile) {
        StringBuilder sb = new StringBuilder();
        if (teile != null) {
            for (String teil : teile) {
                if (teil != null && !teil.isBlank()) {
                    sb.append(sb.isEmpty() ? "" : " ").append(teil);
                }
            }
        }
        String normal = normalisiere(sb.toString());
        return normal == null || normal.length() <= MAX_LAENGE ? normal : normal.substring(0, MAX_LAENGE);
    }

    /**
     * Zerlegt eine Sucheingabe in normalisierte Suchwörter (ohne Doppelte, höchstens
     * {@link #MAX_WOERTER}). Wörter unter {@link #MIN_EINGABE} Zeichen fallen weg –
     * „a“ fände fast jede Position.
     */
    public static List<String> suchwoerter(String eingabe) {
        if (eingabe == null) {
            return List.of();
        }
        String gekuerzt = eingabe.length() > MAX_EINGABE ? eingabe.substring(0, MAX_EINGABE) : eingabe;
        String normal = normalisiere(gekuerzt);
        if (normal == null || normal.length() < MIN_EINGABE) {
            return List.of();
        }
        Set<String> woerter = new LinkedHashSet<>();
        for (String wort : normal.split(" ")) {
            if (wort.length() >= MIN_EINGABE && woerter.size() < MAX_WOERTER) {
                woerter.add(wort);
            }
        }
        return new ArrayList<>(woerter);
    }

    /**
     * LIKE-Muster „enthält“ – {@code !}, {@code %} und {@code _} werden mit {@code !}
     * maskiert; passend zu {@code ESCAPE '!'} in
     * {@link org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository#suche}.
     * Bewusst kein Backslash: MySQL deutet ihn in String-Literalen selbst als Escape.
     */
    public static String enthaeltMuster(String wort) {
        String maskiert = wort.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + maskiert + "%";
    }

    /**
     * Charge für den Vergleich: nur Buchstaben und Ziffern, groß. Zu kurze oder
     * leere Chargen → {@code null}.
     */
    public static String charge(String charge) {
        if (charge == null) {
            return null;
        }
        String c = NICHT_ALNUM.matcher(charge).replaceAll("").toUpperCase(Locale.ROOT);
        return c.length() < MIN_CHARGE ? null : c;
    }
}
