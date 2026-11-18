package org.example.kalkulationsprogramm.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sicherheitsnetz nach der E-Mail-KI: ersetzt Gedankenstriche, die als Satzzeichen
 * dienen ({@code –}, {@code —}, {@code --}, {@code -} mit Leerzeichen), durch Komma bzw.
 * lässt sie neben anderen Satzzeichen ersatzlos weg.
 *
 * <p>Hintergrund: Gehäufte Gedankenstriche gelten als typisches Merkmal von KI-Texten
 * (Muster 16 im Katalog des Skills „humanizer-de“, MIT-Lizenz, siehe
 * {@code THIRD-PARTY-LICENSES.md}). Die KI wird im Prompt schon darum gebeten, sie zu
 * vermeiden; diese Klasse fängt ab, was trotzdem durchrutscht.
 *
 * <p>Unverändert bleiben: Bindestriche in Wörtern („E-Mail“, „Metallbau-Meister“),
 * Ergänzungsstriche („Metallbau- und …“), Bereiche ohne Leerzeichen („8–10“, „Mo–Fr“),
 * Bereiche zwischen Zahlen („8 - 10 Uhr“), ein Minus vor einer Zahl („-5 °C“) und
 * Aufzählungsstriche am Zeilenanfang. Ein Strich zwischen zwei Wochentagen („Mo - Fr“,
 * „Montag - Freitag“, „Mo—Fr“) wird zum Bis-Strich ohne Leerzeichen („Mo–Fr“).
 *
 * <p>Arbeitet auf reinem Text ({@code \n} = Zeilenanfang). Alle Muster sind ReDoS-sicher:
 * possessive Quantifizierer, und ein Leerzeichen-Lauf wird nur an seinem Anfang probiert.
 */
public final class GedankenstrichBereinigung {

    /** Strich mit Leerzeichen davor (Lauf nur ab seinem Anfang, sonst quadratische Laufzeit). */
    private static final Pattern STRICH_NACH_LEERZEICHEN =
            Pattern.compile("(?<!\\h)\\h++(?:—|–|--|-)(\\h*+)");
    /** Gedankenstrich ohne Leerzeichen davor zwischen zwei Wörtern: „Geländer—aus“. */
    private static final Pattern STRICH_ZWISCHEN_WOERTERN =
            Pattern.compile("(?<=\\p{L})(?:—|--)\\h*+(?=\\p{L})");
    /** Gedankenstrich direkt nach einer öffnenden Klammer: „(– oder …)“. */
    private static final Pattern STRICH_NACH_KLAMMER =
            Pattern.compile("(?<=\\()\\h*+(?:—|–|--)\\h++");

    private static final String SATZZEICHEN_DAVOR = ",;:.!?";
    private static final String BIS_STRICH = "–";
    private static final Set<String> WOCHENTAGE = Set.of(
            "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So",
            "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonnabend", "Sonntag");
    private static final String SATZZEICHEN_DANACH = ".,;:!?)";

    private GedankenstrichBereinigung() {
    }

    /** Liefert den Text ohne Gedankenstriche als Satzzeichen; {@code null} bleibt {@code null}. */
    public static String bereinige(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String ergebnis = ersetzeStricheNachLeerzeichen(text);
        String zwischenstand = ergebnis;
        ergebnis = STRICH_ZWISCHEN_WOERTERN.matcher(ergebnis).replaceAll(treffer ->
                zwischenWochentagen(zwischenstand, treffer.start(), treffer.end()) ? BIS_STRICH : ", ");
        return STRICH_NACH_KLAMMER.matcher(ergebnis).replaceAll("");
    }

    private static String ersetzeStricheNachLeerzeichen(String text) {
        Matcher treffer = STRICH_NACH_LEERZEICHEN.matcher(text);
        StringBuilder ergebnis = new StringBuilder(text.length());
        while (treffer.find()) {
            String ersatz = ersatzFuer(text, treffer.start(), treffer.end(), !treffer.group(1).isEmpty());
            treffer.appendReplacement(ergebnis, Matcher.quoteReplacement(ersatz == null ? treffer.group() : ersatz));
        }
        treffer.appendTail(ergebnis);
        return ergebnis.toString();
    }

    /** @return Ersatz für den Treffer oder {@code null}, wenn er stehen bleibt */
    private static String ersatzFuer(String text, int start, int ende, boolean leerzeichenDanach) {
        char davor = start == 0 ? '\n' : text.charAt(start - 1);
        char danach = ende >= text.length() ? '\n' : text.charAt(ende);
        if (davor == '\n') {
            return null; // Aufzählungsstrich am Zeilenanfang
        }
        boolean endetDanach = danach == '\n' || SATZZEICHEN_DANACH.indexOf(danach) >= 0;
        if (endetDanach) {
            return "";
        }
        if (!leerzeichenDanach) {
            return null; // Minus vor einer Zahl („-5 °C“) oder angehängter Strich („–Wort“)
        }
        if (Character.isDigit(davor) && Character.isDigit(danach)) {
            return null; // Bereich zwischen Zahlen: „8 - 10 Uhr“
        }
        if (zwischenWochentagen(text, start, ende)) {
            return BIS_STRICH; // „Mo - Fr“ → „Mo–Fr“
        }
        if (davor == '(') {
            return "";
        }
        return SATZZEICHEN_DAVOR.indexOf(davor) >= 0 ? " " : ", ";
    }

    /** Stehen direkt vor und hinter dem Strich Wochentage („Mo“, „Mo.“, „Montag“)? */
    private static boolean zwischenWochentagen(String text, int start, int ende) {
        int wortEnde = start;
        if (wortEnde > 0 && text.charAt(wortEnde - 1) == '.') {
            wortEnde--;
        }
        int wortAnfang = wortEnde;
        while (wortAnfang > 0 && Character.isLetter(text.charAt(wortAnfang - 1))) {
            wortAnfang--;
        }
        int folgeEnde = ende;
        while (folgeEnde < text.length() && Character.isLetter(text.charAt(folgeEnde))) {
            folgeEnde++;
        }
        return WOCHENTAGE.contains(text.substring(wortAnfang, wortEnde))
                && WOCHENTAGE.contains(text.substring(ende, folgeEnde));
    }
}
