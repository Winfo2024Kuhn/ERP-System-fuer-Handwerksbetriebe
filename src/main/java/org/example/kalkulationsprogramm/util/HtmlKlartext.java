package org.example.kalkulationsprogramm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macht aus HTML-Schnipseln reinen Text – für PDF-Text, Vorschauen und Titel.
 *
 * <p>Backend-Gegenstück zu {@code stripHtmlTags()} und {@code unescapeHtmlEntities()} aus
 * {@code react-pc-frontend/src/lib/htmlSanitizer.ts}. Das Ergebnis ist Text, kein HTML: es darf
 * nie wieder ungeprüft in HTML eingesetzt werden.
 *
 * <ul>
 *   <li>Tags fallen per Fixpunktschleife weg, von innen nach außen. Ein einzelner Durchlauf mit
 *       {@code <[^>]*>} ließe aus {@code <<script>script>} wieder ein Tag entstehen.</li>
 *   <li>Entitäten werden in einem einzigen Durchlauf entpackt. Nacheinander geschaltete
 *       {@code replace("&amp;", "&").replace("&lt;", "<")} machten aus {@code &amp;lt;} ein
 *       {@code <} statt {@code &lt;}.</li>
 * </ul>
 */
public final class HtmlKlartext {

    /** Innerstes Tag: zwischen den Klammern keine weitere Klammer; possessiv gegen Backtracking. */
    private static final Pattern INNERSTES_TAG = Pattern.compile("<[^<>]*+>");
    private static final Pattern ENTITAET = Pattern.compile("&(?:amp|lt|gt|quot|#39|nbsp);");
    private static final Pattern SPITZE_KLAMMER = Pattern.compile("[<>]");
    /** Schutz vor Endlosschleifen; danach werden übrige Klammern restlos entfernt (fail-closed). */
    private static final int MAX_DURCHLAEUFE = 200;

    private HtmlKlartext() {
    }

    /** Entfernt alle Tags ersatzlos. */
    public static String entferneTags(String html) {
        return entferneTags(html, "");
    }

    /**
     * Entfernt alle Tags und setzt {@code ersatz} an ihre Stelle, z. B. ein Leerzeichen, damit
     * Wörter aus benachbarten Absätzen getrennt bleiben.
     */
    public static String entferneTags(String html, String ersatz) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String ersatzWoertlich = Matcher.quoteReplacement(ersatz == null ? "" : ersatz);
        String vorher;
        String ergebnis = html;
        int durchlaeufe = 0;
        do {
            vorher = ergebnis;
            ergebnis = INNERSTES_TAG.matcher(ergebnis).replaceAll(ersatzWoertlich);
            if (++durchlaeufe > MAX_DURCHLAEUFE) {
                return SPITZE_KLAMMER.matcher(ergebnis).replaceAll("");
            }
        } while (!ergebnis.equals(vorher));
        return ergebnis;
    }

    /**
     * Entpackt {@code &amp; &lt; &gt; &quot; &#39; &nbsp;} in einem einzigen Durchlauf.
     * Andere Entitäten (z. B. {@code &auml;}, {@code &#x27;}) bleiben bewusst stehen – wie im
     * Frontend und wie bisher im Backend.
     */
    public static String entpackeEntitaeten(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ENTITAET.matcher(text).replaceAll(treffer -> Matcher.quoteReplacement(zeichenFuer(treffer.group())));
    }

    /**
     * Tags entfernen, dann Entitäten entpacken. Diese Reihenfolge ist Pflicht: ein maskiertes
     * {@code &lt;b&gt;} ist Text und bleibt als {@code <b>} stehen. Leerraum bleibt unverändert.
     */
    public static String zuText(String html) {
        return entpackeEntitaeten(entferneTags(html));
    }

    private static String zeichenFuer(String entitaet) {
        return switch (entitaet) {
            case "&amp;" -> "&";
            case "&lt;" -> "<";
            case "&gt;" -> ">";
            case "&quot;" -> "\"";
            case "&#39;" -> "'";
            default -> " "; // &nbsp; – das Muster lässt nichts anderes durch
        };
    }
}
