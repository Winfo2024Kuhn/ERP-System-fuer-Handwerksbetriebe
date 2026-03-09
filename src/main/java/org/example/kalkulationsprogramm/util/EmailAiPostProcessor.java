package org.example.kalkulationsprogramm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nachbearbeitungs-Helfer für KI-generierte E-Mail-Texte.
 * Ziel: Nur den bereinigten E-Mail-Inhalt ohne einleitende Zeilen,
 * Codeblöcke oder Überschriften zurückgeben und Absätze sowie Aufzählungen erhalten.
 */
public final class EmailAiPostProcessor {

    private EmailAiPostProcessor() {}

    /**
     * Bereinigt KI-Antworten: entfernt Codeblöcke, Markdown-Überschriften und typische
     * deutsche Einleitungszeilen wie "Hier ist der überarbeitete Text:".
     * Absätze und Aufzählungszeichen bleiben erhalten.
     */
    public static String sanitizePlainText(String input) {
        if (input == null) return null;
        String s = input.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (s.isEmpty()) return "";

        // Entfernt umschließende Dreifach-Backticks (optional mit Sprache)
        s = stripCodeFences(s).trim();

        // Entfernt führende Markdown-Überschriften wie "# ..." oder "### ..."
        s = removeLeadingMarkdownHeadings(s).trim();

        // Streicht einleitende Zeilen, wenn sie wie eine Intro wirken (endet mit ':' und enthält Schlüsselwörter)
        s = removeLeadingPrefaceLines(s).trim();

        // Entfernt paarige Anführungszeichen, wenn der gesamte Inhalt darin eingeschlossen ist
        s = unwrapSymmetricQuotes(s).trim();

        // Reduziert übermäßige Leerzeilen auf höchstens zwei
        s = s.replaceAll("\n{3,}", "\n\n");
        // Entfernt gängige deutsche Schlussformeln am Ende
        s = stripTrailingClosings(s);
        return s;
    }

    /** Entfernt führende und abschließende Markdown-Codeblöcke inklusive optionaler Sprache. */
    static String stripCodeFences(String s) {
        // führender Codeblock
        s = s.replaceFirst("(?s)^\n*```[a-zA-Z0-9_-]*\n?", "");
        // abschließender Codeblock
        s = s.replaceFirst("(?s)\n?```\n*$", "");
        return s;
    }

    /** Filtert einleitende Markdown-Überschriften, sodass nur der eigentliche Inhalt bleibt. */
    static String removeLeadingMarkdownHeadings(String s) {
        String[] lines = s.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String ln = lines[i].trim();
            if (ln.isEmpty()) { i++; continue; }
            if (ln.startsWith("#")) { i++; continue; }
            break;
        }
        return joinFrom(lines, i);
    }

    /**
     * Entfernt typische Einleitungsphrasen wie "Hier ist die überarbeitete Version", die
     * Sprachmodelle häufig voranstellen.
     */
    static String removeLeadingPrefaceLines(String s) {
        String[] lines = s.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String ln = lines[i].trim();
            if (ln.isEmpty()) { i++; continue; }
            String lower = ln.toLowerCase(Locale.ROOT);

            boolean isHeadingLike = ln.endsWith(":");
            boolean containsIntroKeyword =
                    lower.contains("hier ist") ||
                    lower.contains("überarbeitet") || lower.contains("ueberarbeitet") ||
                    lower.contains("verbessert") || lower.contains("optimiert") ||
                    lower.contains("version:") || lower.contains("text:") ||
                    lower.contains("e-mail:") || lower.contains("email:") || lower.contains("mail:") ||
                    lower.startsWith("verbesserte version") ||
                    lower.startsWith("überarbeitete version") || lower.startsWith("ueberarbeitete version") ||
                    lower.startsWith("überarbeiteter text") || lower.startsWith("ueberarbeiteter text") ||
                    lower.startsWith("optimierte e-mail") || lower.startsWith("optimierte email") ||
                    lower.startsWith("antwort:") || lower.equals("antwort");

            boolean politeIntroThenColon =
                    (lower.startsWith("gerne") || lower.startsWith("gern ") || lower.startsWith("natürlich") ||
                     lower.startsWith("natuerlich") || lower.startsWith("selbstverständlich") ||
                     lower.startsWith("selbstverstaendlich") || lower.startsWith("klar") || lower.startsWith("sicher"))
                            && (ln.contains(":") || lower.contains("hier ist"));

            if (isHeadingLike && containsIntroKeyword) { i++; continue; }
            if (politeIntroThenColon) { i++; continue; }
            break;
        }
        return joinFrom(lines, i);
    }

    /** Schneidet paarige Anführungszeichen am Anfang und Ende des Textes ab. */
    static String unwrapSymmetricQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                // Prüft nicht weiter auf unpassende Anführungszeichen dazwischen; wird vorsichtig entfernt
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    /**
     * Kappt häufige Grußformeln am Textende, damit nur der eigentliche E-Mail-Inhalt
     * zurückgeliefert wird.
     */
    static String stripTrailingClosings(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] lines = s.split("\n", -1);
        // Entfernt nachfolgende Leerzeilen am Ende
        int end = lines.length - 1;
        while (end >= 0 && lines[end].trim().isEmpty()) end--;
        if (end < 0) return "";

        int lastClosing = -1;
        for (int i = 0; i <= end; i++) {
            String ln = lines[i].trim().toLowerCase(Locale.ROOT);
            if (isClosingLine(ln)) {
                lastClosing = i;
            }
        }
        if (lastClosing >= 0 && (end - lastClosing) <= 4) {
            // Entfernt ab der Grußzeile bis zum Ende alles
            end = lastClosing - 1;
            while (end >= 0 && lines[end].trim().isEmpty()) end--;
        }
        if (end < 0) return "";
        List<String> out = new ArrayList<>(end + 1);
        for (int i = 0; i <= end; i++) out.add(lines[i]);
        return String.join("\n", out).trim();
    }

    /**
     * Ermittelt, ob eine Zeile wie eine Grußformel aussieht – sowohl deutsch als auch
     * englisch.
     */
    private static boolean isClosingLine(String lower) {
        if (lower == null) return false;
        lower = lower.trim();
        if (lower.isEmpty()) return false;
        return lower.startsWith("mit freundlichen gr") ||
               lower.startsWith("viele gr") ||
               lower.startsWith("freundliche gr") ||
               lower.startsWith("beste gr") ||
               lower.startsWith("herzliche gr") ||
               lower.equals("gruss") || lower.equals("gruesse") ||
               lower.equals("vg") || lower.equals("lg") || lower.equals("mfg") ||
               lower.startsWith("best regards") || lower.startsWith("kind regards");
    }

    /** Fügt ein Array von Zeilen ab einem Index wieder zu einem Text zusammen. */
    private static String joinFrom(String[] lines, int start) {
        if (start <= 0) return String.join("\n", lines);
        List<String> out = new ArrayList<>(lines.length - start);
        for (int i = start; i < lines.length; i++) {
            out.add(lines[i]);
        }
        return String.join("\n", out);
    }
}
