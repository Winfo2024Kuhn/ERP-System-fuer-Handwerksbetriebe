package org.example.kalkulationsprogramm.util;

import java.util.regex.Pattern;

/**
 * Bereitet frei eingegebenen Client-Text fuer eine einzeilige Stelle im PDF vor (z. B. die
 * Unterzeilen im Abrechnungsstand der Rechnung): Steuer- und Formatzeichen sowie Zeilenumbrueche werden zu
 * Leerzeichen, aussen wird getrimmt, und ueberlange Texte werden mit „…“ auf die Grenze
 * gekuerzt, damit sie das Layout nicht zerlegen.
 *
 * <p>Der Text bleibt Klartext: HTML wird weder ausgewertet noch entfernt, OpenPDF setzt
 * {@code <script>} woertlich. Reine Klasse ohne Spring.
 */
public final class DruckTextBegrenzung {

    /**
     * Steuerzeichen (C0 und C1, inkl. Zeilenumbruch und Tab), unsichtbare Formatzeichen
     * (z. B. U+202E Richtungsumkehr, U+200B Zero-Width-Space, U+FEFF) sowie Unicode-Zeilen-/Absatztrenner.
     */
    private static final Pattern STEUERZEICHEN = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]");

    private DruckTextBegrenzung() {
    }

    /**
     * @param text       Client-Text, darf {@code null} sein
     * @param maxZeichen Obergrenze in Zeichen (Unicode-Codepoints, mindestens 2), inklusive „…“
     * @return bereinigter Text, oder {@code null}, wenn nichts Druckbares uebrig bleibt
     */
    public static String begrenze(String text, int maxZeichen) {
        if (text == null) {
            return null;
        }
        String einzeilig = STEUERZEICHEN.matcher(text).replaceAll(" ").strip();
        if (einzeilig.isEmpty()) {
            return null;
        }
        if (einzeilig.codePointCount(0, einzeilig.length()) <= maxZeichen) {
            return einzeilig;
        }
        int ende = einzeilig.offsetByCodePoints(0, maxZeichen - 1);
        return einzeilig.substring(0, ende) + "…";
    }
}
