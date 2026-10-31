package org.example.kalkulationsprogramm.service.spam;

import java.util.List;
import java.util.Locale;

/**
 * Wortsuche ohne reguläre Ausdrücke: linear in der Textlänge, damit auch sehr lange
 * Mails oder Anzeigenamen kein Backtracking auslösen. Groß/Klein und Umlaute werden über
 * {@link #normalisiere(String)} vereinheitlicht ({@code Änderung} → {@code aenderung}).
 */
final class Textsuche {

    /** Höchstens so viele Fundstellen je Wort werden auf Nachbarschaft geprüft. */
    private static final int MAX_FUNDSTELLEN = 50;

    private Textsuche() {
    }

    /**
     * Kleinbuchstaben, Umlaute als ae/oe/ue/ss, jede Folge von Leerraum als ein Leerzeichen.
     * Texte über {@code maxLaenge} werden vorher abgeschnitten.
     */
    static String normalisiere(String text, int maxLaenge) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String quelle = text.length() > maxLaenge ? text.substring(0, maxLaenge) : text;
        String klein = quelle.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(klein.length() + 16);
        boolean leerraum = false;
        for (int i = 0; i < klein.length(); i++) {
            char c = klein.charAt(i);
            if (Character.isWhitespace(c) || c == '\u00A0') {
                leerraum = true;
                continue;
            }
            if (leerraum && sb.length() > 0) {
                sb.append(' ');
            }
            leerraum = false;
            switch (c) {
                case 'ä' -> sb.append("ae");
                case 'ö' -> sb.append("oe");
                case 'ü' -> sb.append("ue");
                case 'ß' -> sb.append("ss");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String normalisiere(String text) {
        return normalisiere(text, Integer.MAX_VALUE);
    }

    /** Kommt {@code wort} als ganzes Wort vor (links und rechts kein Buchstabe/keine Ziffer)? */
    static boolean enthaeltWort(String text, String wort) {
        return finde(text, wort, 0, true) >= 0;
    }

    /** Beginnt irgendwo ein Wort mit {@code stamm} ({@code zahlung} findet {@code zahlungsfrist})? */
    static boolean enthaeltWortanfang(String text, String stamm) {
        return finde(text, stamm, 0, false) >= 0;
    }

    static boolean enthaeltIrgendeinWort(String text, List<String> woerter) {
        for (String wort : woerter) {
            if (enthaeltWort(text, wort)) {
                return true;
            }
        }
        return false;
    }

    static boolean enthaeltIrgendeinenWortanfang(String text, List<String> staemme) {
        for (String stamm : staemme) {
            if (enthaeltWortanfang(text, stamm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Steht ein Wortanfang aus {@code anker} höchstens {@code abstand} Zeichen vor oder nach
     * einem Wortanfang aus {@code nachbarn}?
     */
    static boolean naheBeieinander(String text, List<String> anker, List<String> nachbarn, int abstand) {
        for (String a : anker) {
            int pos = finde(text, a, 0, false);
            int gesehen = 0;
            while (pos >= 0 && gesehen < MAX_FUNDSTELLEN) {
                int von = Math.max(0, pos - abstand);
                int bis = Math.min(text.length(), pos + a.length() + abstand);
                String fenster = text.substring(von, bis);
                for (String n : nachbarn) {
                    if (finde(fenster, n, 0, false) >= 0) {
                        return true;
                    }
                }
                gesehen++;
                pos = finde(text, a, pos + 1, false);
            }
        }
        return false;
    }

    private static int finde(String text, String wort, int ab, boolean ganzesWort) {
        if (text == null || wort == null || wort.isEmpty()) {
            return -1;
        }
        int pos = text.indexOf(wort, ab);
        while (pos >= 0) {
            boolean linksFrei = pos == 0 || !Character.isLetterOrDigit(text.charAt(pos - 1));
            int ende = pos + wort.length();
            boolean rechtsFrei = !ganzesWort || ende >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(ende));
            if (linksFrei && rechtsFrei) {
                return pos;
            }
            pos = text.indexOf(wort, pos + 1);
        }
        return -1;
    }
}
