package org.example.kalkulationsprogramm.service.emailki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bereinigt das HTML, das bei der E-Mail-KI hinein- und herausgeht.
 *
 * <ul>
 *   <li>{@link #bereinigeText}: der Text der Mail, wie ihn die KI sehen und liefern darf, also
 *       alles, was die Formatleiste des Mail-Editors erzeugt: Absätze, Umbrüche, Überschriften
 *       {@code h1}–{@code h4}, Einzug ({@code blockquote}), fett/kursiv/unterstrichen/durchgestrichen
 *       ({@code s}, {@code strike}), Listen, Links ({@code http}, {@code https}, {@code mailto}) und
 *       wenige Stile an {@code p}, {@code div}, {@code h1}–{@code h4}, {@code blockquote} und
 *       {@code span} (Farbe, Hintergrund, Schriftgröße, -stärke, -stil, Textdekoration, Ausrichtung,
 *       Einzug über {@code margin}/{@code padding}); dazu {@code align} mit
 *       {@code left/center/right/justify}. Alles andere fällt weg, der Text bleibt.</li>
 *   <li>{@link #bereinigeFestesElement}: Tabellen und Bilder, die die KI nie sieht und die
 *       unverändert zurückkommen. Gefährliches (Skripte, Ereignis-Attribute, fremde
 *       Protokolle, Stile mit {@code url(…)}/{@code expression}) fällt weg, Inline-Stile und
 *       Tabellen-Attribute bleiben. Die Liste ist enger als
 *       {@code EmailHtmlSanitizer.sanitizeDetailHtml}, nie lockerer.</li>
 * </ul>
 */
public final class EmailKiHtmlBereinigung {

    /** Elemente, an denen Stile (und bei Blöcken die Ausrichtung) erlaubt sind. */
    private static final String[] MIT_STIL = {"p", "div", "h1", "h2", "h3", "h4", "blockquote", "span"};
    private static final String[] MIT_AUSRICHTUNG = {"p", "div", "h1", "h2", "h3", "h4"};

    private static final Safelist TEXT = Safelist.none()
            .addTags("p", "br", "div", "h1", "h2", "h3", "h4", "blockquote", "strong", "b", "em", "i", "u", "s",
                    "strike", "ul", "ol", "li", "a", "span")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");

    static {
        for (String tag : MIT_STIL) {
            TEXT.addAttributes(tag, "style");
        }
        for (String tag : MIT_AUSRICHTUNG) {
            TEXT.addAttributes(tag, "align");
        }
    }

    private static final Safelist FESTE_ELEMENTE = Safelist.relaxed()
            .addTags("s", "font")
            .addAttributes(":all", "style", "width", "height", "border", "cellpadding", "cellspacing",
                    "align", "valign", "bgcolor", "colspan", "rowspan", "color", "face", "size")
            .removeProtocols("a", "href", "ftp")
            .addProtocols("img", "src", "cid", "data");

    private static final Set<String> ERLAUBTE_STILE = Set.of(
            "color", "background-color", "font-size", "font-weight", "font-style", "text-decoration",
            "text-align", "margin", "margin-left", "padding", "padding-left");
    private static final Set<String> ERLAUBTE_AUSRICHTUNG = Set.of("left", "center", "right", "justify");

    /** Erlaubte Zeichen in einem Stilwert; keine Doppelpunkte, Schrägstriche, Anführungszeichen. */
    private static final Pattern STILWERT = Pattern.compile("[-\\w\\s#.,%()]++");
    private static final List<String> GEFAEHRLICHE_STILTEILE = List.of(
            "url(", "expression", "javascript:", "behavior", "-moz-binding", "@import", "\\", "/*");
    private static final Pattern ABSATZTRENNER = Pattern.compile("\\n(?:\\h*+\\n)++");

    private EmailKiHtmlBereinigung() {
    }

    /** Text der Mail auf die erlaubte Formatierung zurückschneiden. */
    public static String bereinigeText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(Jsoup.clean(html, "", TEXT, ausgabe()));
        doc.outputSettings(ausgabe());
        for (Element link : doc.select("a")) {
            if (!link.hasAttr("href")) {
                link.unwrap();
            }
        }
        for (Element mitStil : doc.select("[style]")) {
            String stil = nurErlaubteStile(mitStil.attr("style"));
            if (stil.isEmpty()) {
                mitStil.removeAttr("style");
            } else {
                mitStil.attr("style", stil);
            }
        }
        for (Element ausgerichtet : doc.select("[align]")) {
            if (!ERLAUBTE_AUSRICHTUNG.contains(ausgerichtet.attr("align").strip().toLowerCase(Locale.ROOT))) {
                ausgerichtet.removeAttr("align");
            }
        }
        for (Element span : doc.select("span")) {
            if (span.attributesSize() == 0) {
                span.unwrap();
            }
        }
        return doc.body().html().trim();
    }

    /** Tabelle oder Bild bereinigen, sonst unverändert lassen. */
    public static String bereinigeFestesElement(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(Jsoup.clean(html, "", FESTE_ELEMENTE, ausgabe()));
        doc.outputSettings(ausgabe());
        for (Element mitStil : doc.select("[style]")) {
            if (istGefaehrlich(mitStil.attr("style"))) {
                mitStil.removeAttr("style");
            }
        }
        return doc.body().html().trim();
    }

    /** {@code true}, wenn der Text kein einziges HTML-Element enthält. */
    public static boolean istKlartext(String text) {
        return text == null || Jsoup.parseBodyFragment(text).body().children().isEmpty();
    }

    /** Klartext als Absätze: Leerzeilen trennen Absätze, einfache Umbrüche werden {@code <br>}. */
    public static String textAlsHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment("");
        doc.outputSettings(ausgabe());
        String normalisiert = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        for (String absatz : ABSATZTRENNER.split(normalisiert)) {
            Element p = doc.body().appendElement("p");
            String[] zeilen = absatz.strip().split("\n");
            for (int i = 0; i < zeilen.length; i++) {
                if (i > 0) {
                    p.appendElement("br");
                }
                p.appendText(zeilen[i].strip());
            }
        }
        return doc.body().html();
    }

    static Document.OutputSettings ausgabe() {
        return new Document.OutputSettings().prettyPrint(false);
    }

    private static String nurErlaubteStile(String stil) {
        List<String> erlaubt = new ArrayList<>();
        for (String deklaration : stil.split(";")) {
            int trenner = deklaration.indexOf(':');
            if (trenner <= 0) {
                continue;
            }
            String name = deklaration.substring(0, trenner).strip().toLowerCase(Locale.ROOT);
            String wert = deklaration.substring(trenner + 1).strip();
            if (ERLAUBTE_STILE.contains(name) && STILWERT.matcher(wert).matches() && !istGefaehrlich(wert)) {
                erlaubt.add(name + ": " + wert);
            }
        }
        return String.join("; ", erlaubt);
    }

    private static boolean istGefaehrlich(String stil) {
        String klein = stil.toLowerCase(Locale.ROOT);
        return GEFAEHRLICHE_STILTEILE.stream().anyMatch(klein::contains);
    }
}
