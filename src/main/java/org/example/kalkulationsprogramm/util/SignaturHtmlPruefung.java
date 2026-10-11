package org.example.kalkulationsprogramm.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bereinigt und prüft das HTML einer E-Mail-Signatur, bevor es gespeichert wird.
 *
 * <p>Erlaubt ist, was eine Signatur aus dem Signatur-Studio braucht: Tabellen-Layout,
 * Inline-Styles, Bilder per Link ({@code http/https}), als eingebettetes Bild
 * ({@code data:image/png|jpeg|gif|webp;base64,…}) oder per {@code cid:} (alte Datei-Uploads),
 * Links auf Websites, E-Mail-Adressen und Telefonnummern. Skripte, Event-Handler und
 * {@code javascript:}-Links fliegen raus.</p>
 *
 * <p>Fehler werfen eine {@link IllegalArgumentException} mit einer Meldung, die direkt im
 * Frontend angezeigt werden kann.</p>
 */
public final class SignaturHtmlPruefung {

    /** Höchstlänge für HTML und Formulardaten – reicht für mehrere eingebettete Bilder. */
    public static final int MAX_ZEICHEN = 3_000_000;
    /** Höchstgröße eines eingebetteten Bildes (nach dem Entpacken). */
    public static final int MAX_BILD_BYTES = 1_048_576;
    public static final int MAX_NAME_ZEICHEN = 200;

    private static final Safelist SIGNATUR_SAFELIST = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col",
                    "span", "font", "center", "hr", "div", "p", "br", "h1", "h2", "h3", "h4", "h5", "h6")
            .addAttributes(":all", "style", "width", "height", "border", "cellpadding",
                    "cellspacing", "align", "valign", "bgcolor", "color", "face", "size", "role",
                    "aria-label", "title", "data-signature-id", "data-system-placeholder")
            .addAttributes("img", "alt", "src")
            .addAttributes("a", "href", "target", "rel")
            .addProtocols("img", "src", "http", "https", "cid", "data")
            .addProtocols("a", "href", "http", "https", "mailto", "tel");

    /** Eingebettetes Bild: erlaubter Typ, sauberes Base64 (possessiv – kein Backtracking). */
    private static final Pattern DATEN_BILD = Pattern.compile(
            "(?i)^data:image/(png|jpe?g|gif|webp);base64,([A-Za-z0-9+/]++=*+)$");

    private SignaturHtmlPruefung() {
    }

    /** Prüft den Namen und liefert ihn ohne Leerzeichen am Rand. */
    public static String pruefeName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new IllegalArgumentException("Bitte geben Sie der Signatur einen Namen.");
        }
        if (n.length() > MAX_NAME_ZEICHEN) {
            throw new IllegalArgumentException("Der Name ist zu lang (höchstens " + MAX_NAME_ZEICHEN + " Zeichen).");
        }
        return n;
    }

    /** Prüft die Formulardaten des Studios: leer = {@code null}, sonst höchstens {@link #MAX_ZEICHEN}. */
    public static String pruefeVorlageDaten(String vorlageDaten) {
        if (vorlageDaten == null || vorlageDaten.isBlank()) {
            return null;
        }
        if (vorlageDaten.length() > MAX_ZEICHEN) {
            throw new IllegalArgumentException("Die Signatur ist zu groß. Bitte kleinere Bilder verwenden.");
        }
        return vorlageDaten;
    }

    /**
     * Bereinigt das Signatur-HTML und prüft eingebettete Bilder.
     *
     * @return bereinigtes HTML
     * @throws IllegalArgumentException bei leerem/zu großem HTML oder unerlaubten Bildern
     */
    public static String bereinige(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Die Signatur ist leer.");
        }
        if (html.length() > MAX_ZEICHEN) {
            throw new IllegalArgumentException("Die Signatur ist zu groß. Bitte kleinere Bilder verwenden.");
        }
        Document.OutputSettings ausgabe = new Document.OutputSettings().prettyPrint(false);
        String sauber = Jsoup.clean(html, "", SIGNATUR_SAFELIST, ausgabe).trim();

        Document doc = Jsoup.parseBodyFragment(sauber);
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").trim();
            if (src.regionMatches(true, 0, "data:", 0, 5)) {
                pruefeDatenBild(src);
            }
        }
        if (sauber.isEmpty()) {
            throw new IllegalArgumentException("Die Signatur ist leer.");
        }
        return sauber;
    }

    private static void pruefeDatenBild(String src) {
        Matcher m = DATEN_BILD.matcher(src);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Ein eingebettetes Bild hat ein unbekanntes Format. Erlaubt sind PNG, JPG, GIF und WebP.");
        }
        byte[] daten;
        try {
            daten = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ein eingebettetes Bild ist beschädigt. Bitte neu hochladen.");
        }
        if (daten.length > MAX_BILD_BYTES) {
            throw new IllegalArgumentException("Ein Bild ist zu groß (höchstens 1 MB). Bitte ein kleineres Bild verwenden.");
        }
    }
}
