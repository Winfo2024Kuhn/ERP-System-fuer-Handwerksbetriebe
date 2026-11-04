package org.example.kalkulationsprogramm.util;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Anrede;

/**
 * Eine Regel für die Briefanrede, überall im Backend gleich (und gleich wie im Frontend):
 * <ul>
 *   <li><b>Anredetext</b> ({@code {{ANREDE}}} allein): „Sehr geehrter Herr“, „Sehr geehrte Frau“,
 *       „Sehr geehrte Familie“; bei Firma, „Damen und Herren“ oder ohne Anrede-Art
 *       „Sehr geehrte Damen und Herren“.</li>
 *   <li><b>Briefanrede</b> (Anrede + Name): nur bei Herr/Frau/Familie mit Namen
 *       „Sehr geehrter Herr Müller“, sonst immer „Sehr geehrte Damen und Herren“. Ein „Herr“,
 *       „Frau“ oder „Familie“ vorn im Namen fällt weg, damit kein „Herr Herr Müller“ entsteht;
 *       bleibt danach nichts übrig, gilt der Name als leer.</li>
 *   <li><b>Paar-Erkennung</b>: Steht {@code {{ANREDE}}} nur durch Leerraum getrennt vor
 *       {@code {{ANSPRECHPARTNER}}} oder {@code {{KUNDENNAME}}}, wird das Paar zur Briefanrede.
 *       Das gilt nur, wenn der Aufrufer die Anrede-Art über {@link #inKontext} in den
 *       Platzhalter-Kontext gelegt hat. Sonst bleibt ein frei gesetztes {@code ANREDE}
 *       (z. B. „Hallo Max Mustermann“ der Anfrage-Bestätigung) unangetastet.</li>
 * </ul>
 * Paar und Einzel-Platzhalter werden in <b>einem</b> Durchlauf ersetzt ({@link #ersetze}): Was einmal
 * eingesetzt ist (z. B. ein Kundenname „{{DOKUMENTNUMMER}}“), wird nicht noch einmal ausgewertet.
 * Reine Funktionen ohne Spring, damit sie überall (PDF-Textbausteine, E-Mail-Vorlagen) gleich laufen.
 */
public final class Briefanrede {

    public static final String DAMEN_UND_HERREN = "Sehr geehrte Damen und Herren";

    /**
     * Interner Kontext-Schlüssel für die Anrede-Art (Enum-Name, leer = Art bekannt, aber keine).
     * Der Bindestrich ist in Platzhalter-Namen nicht erlaubt: Kein Nutzer-Platzhalter kann ihn
     * treffen, und er taucht in keinem Platzhalter-Katalog auf.
     */
    public static final String ART_SCHLUESSEL = "ANREDE-ART";

    private static final String ANREDE_SCHLUESSEL = "ANREDE";

    /**
     * {@code {{ANREDE}}}, Leerraum (auch {@code &nbsp;}), dann Ansprechpartner oder Kundenname.
     * Possessiv (kein ReDoS); nur dieser Teil ist Groß/klein-unabhängig ({@code (?i:…)}).
     */
    private static final String PAAR =
            "(?i:\\{\\{\\s*+ANREDE\\s*+\\}\\}(?:\\s|\\u00A0|&nbsp;)*+\\{\\{\\s*+(ANSPRECHPARTNER|KUNDENNAME)\\s*+\\}\\})";

    /** „Herr“, „Frau“, „Familie“ vorn im Namen, gefolgt von Leerraum oder allein stehend. */
    private static final Pattern ANREDE_WORT_VORN = Pattern.compile(
            "^(?:herr|frau|familie)(?:\\s++|$)", Pattern.CASE_INSENSITIVE);

    /**
     * Vorbereitete Muster eines Aufrufers: nur Einzel-Platzhalter (ohne Anrede-Art) und
     * Paar oder Einzel-Platzhalter (mit Anrede-Art). Einmal als Konstante anlegen über {@link #muster}.
     */
    public record Muster(Pattern einzel, Pattern mitPaar) {
    }

    private Briefanrede() {
    }

    /**
     * Baut die Muster für {@link #ersetze}. Der reguläre Ausdruck der Einzel-Platzhalter muss genau
     * eine Gruppe haben (den Platzhalter-Namen) und behält seine eigene Groß-/Kleinschreibung.
     */
    public static Muster muster(String einzelRegex) {
        return new Muster(Pattern.compile(einzelRegex), Pattern.compile(PAAR + "|" + einzelRegex));
    }

    /** Anredetext für {@code {{ANREDE}}} allein; ohne Anrede-Art „Sehr geehrte Damen und Herren“. */
    public static String anredeText(Anrede art) {
        return art == null ? DAMEN_UND_HERREN : art.toAnredeText();
    }

    /** Briefanrede aus Anrede-Art und Name (Ansprechpartner oder Kundenname), ohne Komma. */
    public static String briefanrede(Anrede art, String name) {
        if (art != Anrede.HERR && art != Anrede.FRAU && art != Anrede.FAMILIE) {
            return DAMEN_UND_HERREN;
        }
        String ohneAnredeWort = ANREDE_WORT_VORN.matcher(bereinigt(name)).replaceFirst("");
        return ohneAnredeWort.isEmpty() ? DAMEN_UND_HERREN : art.toAnredeText() + " " + ohneAnredeWort;
    }

    /** Geschütztes Leerzeichen zählt wie ein normales (wie {@code trim()} im Frontend); außen getrimmt. */
    private static String bereinigt(String name) {
        return name == null ? "" : name.replace('\u00A0', ' ').strip();
    }

    /** Legt den Anredetext ({@code ANREDE}) und die Anrede-Art (schaltet die Paar-Erkennung ein) in den Kontext. */
    public static void inKontext(Map<String, String> ctx, Anrede art) {
        ctx.put(ANREDE_SCHLUESSEL, anredeText(art));
        ctx.put(ART_SCHLUESSEL, art == null ? "" : art.name());
    }

    /** Anrede-Art aus dem Kontext; {@code null}, wenn keine oder keine bekannt ist. */
    public static Anrede artAus(Map<String, String> ctx) {
        return ctx == null ? null : Anrede.fromString(ctx.get(ART_SCHLUESSEL));
    }

    /**
     * Ersetzt alle Platzhalter in einem Durchlauf. Mit Anrede-Art im Kontext wird jedes Paar
     * {@code {{ANREDE}} {{ANSPRECHPARTNER}}} bzw. {@code {{ANREDE}} {{KUNDENNAME}}} zur Briefanrede;
     * alle übrigen Platzhalter liefert {@code einzelWert}.
     *
     * @param einzelWert Ersatz für einen Einzel-Platzhalter (bekommt den Namen aus der Gruppe des
     *                   Aufrufer-Musters); {@code null} = Platzhalter bleibt unverändert stehen
     * @param maskierung wird auf die fertige Briefanrede angewendet (z. B. HTML-Maskierung im Mail-Body)
     */
    public static String ersetze(String text, Map<String, String> ctx, Muster muster,
                                 Function<String, String> einzelWert, UnaryOperator<String> maskierung) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        boolean mitPaar = ctx != null && ctx.containsKey(ART_SCHLUESSEL);
        Anrede art = artAus(ctx);
        Matcher matcher = (mitPaar ? muster.mitPaar() : muster.einzel()).matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String ersatz;
            if (mitPaar && matcher.group(1) != null) {
                String name = ctx.get(matcher.group(1).toUpperCase(Locale.ROOT));
                ersatz = maskierung.apply(briefanrede(art, name));
            } else {
                String wert = einzelWert.apply(matcher.group(mitPaar ? 2 : 1));
                ersatz = wert != null ? wert : matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(ersatz));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
