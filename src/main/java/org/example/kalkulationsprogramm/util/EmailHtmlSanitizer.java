package org.example.kalkulationsprogramm.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hilfsfunktionen, um eingehendes E-Mail-HTML zu bereinigen und wichtige
 * Formatierungen
 * zu erhalten sowie eine gut lesbare Textvariante abzuleiten.
 */
public final class EmailHtmlSanitizer {

    private static final Safelist SAFE_LIST = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "blockquote")
            .addAttributes(":all", "class", "style", "data-signature-id")
            .addProtocols("img", "src", "cid", "data");

    private static final Pattern BLOCK_CLOSING_TAG_PATTERN = Pattern.compile(
            "(?i)</(?:p|div|section|article|header|footer|blockquote|ul|ol|li|table|thead|tbody|tfoot|tr|h[1-6])>");

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "header", "footer", "blockquote",
            "ul", "ol", "table", "thead", "tbody", "tfoot", "tr",
            "h1", "h2", "h3", "h4", "h5", "h6");

    private EmailHtmlSanitizer() {
    }

    /**
     * Liefert eine strukturbeibehaltende HTML-Variante für Detailansichten.
     * Entfernt nur gefährliche Elemente (script, onclick etc.) aber behält
     * alle Formatierung inkl. Tabellen für E-Mail-Signaturen.
     */
    public static String sanitizeDetailHtml(String html) {
        if (html == null) {
            return null;
        }

        // Sehr permissive Safelist die alle Formatierung behält
        Safelist permissive = Safelist.relaxed()
                .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th",
                        "colgroup", "col", "blockquote", "span", "font", "center",
                        "hr", "caption", "section", "article", "header", "footer", "nav", "aside")
                .addAttributes(":all", "class", "style", "id", "data-signature-id",
                        "width", "height", "border", "cellpadding", "cellspacing",
                        "align", "valign", "bgcolor", "color", "face", "size")
                .addProtocols("img", "src", "cid", "data", "http", "https")
                .addProtocols("a", "href", "http", "https", "mailto");

        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String clean = Jsoup.clean(html, "", permissive, outputSettings);

        Document doc = Jsoup.parse(clean, "", org.jsoup.parser.Parser.htmlParser());
        doc.outputSettings().prettyPrint(false);

        // Links sicher machen
        for (Element anchor : doc.select("a[href]")) {
            anchor.attr("target", "_blank");
            anchor.attr("rel", "noopener");
        }

        return doc.body().html().trim();
    }

    /**
     * Liefert eine auf <br/>
     * -basierte Vorschau, die sich für Listen und Text-Previews eignet.
     */
    public static String sanitizePreviewHtml(String html) {
        Document doc = prepareDocument(html);
        if (doc == null) {
            return null;
        }
        for (String tag : BLOCK_TAGS) {
            convertBlocksToBreaks(doc, tag);
        }
        return finalizePreviewHtml(doc);
    }

    /**
     * Liefert eine auf {@code <br/>
     * }-basierte Vorschau, die maximal {@code maxParagraphs} Absätze enthält.
     * HTML-Entities werden vor dem Bereinigen dekodiert, damit Word-/Outlook-Hüllen
     * entfernt werden können.
     */
    public static String limitPreviewHtml(String html, int maxParagraphs) {
        if (html == null) {
            return null;
        }
        if (maxParagraphs <= 0) {
            return "";
        }
        String decoded = org.jsoup.parser.Parser.unescapeEntities(html, false);
        String sanitized = sanitizePreviewHtml(decoded);
        if (sanitized == null) {
            return null;
        }
        String normalized = sanitized.replaceAll("(?i)<br\\s*/?>", "<br/>");
        normalized = normalized.replaceAll("(?i)^(<br/?>\\s*)+", "");
        String[] parts = normalized.split("(?i)<br/><br/>");
        if (parts.length <= maxParagraphs) {
            return normalized;
        }
        StringBuilder sb = new StringBuilder();
        int emitted = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (emitted > 0) {
                sb.append("<br/><br/>");
            }
            sb.append(part);
            emitted++;
            if (emitted >= maxParagraphs) {
                break;
            }
        }
        return sb.length() == 0 ? "" : sb.toString().trim();
    }

    /**
     * Wandelt HTML in eine gut lesbare Textdarstellung um, indem Tags entfernt und
     * Zeilenumbrüche sowie HTML-Entitäten bereinigt werden.
     */
    public static String htmlToPlainText(String html) {
        if (html == null) {
            return null;
        }
        String s = html.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = BLOCK_CLOSING_TAG_PATTERN.matcher(s).replaceAll("\n\n");
        s = s.replaceAll("(?is)<[^>]+>", "");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        s = s.replaceAll("[ \t]+\n", "\n");
        s = s.replaceAll("(?m)^[ \t]+", "");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    /**
     * Formatiert reinen Text so, dass Absätze und Zeilenumbrüche wie in typischen
     * Mail-Clients
     * angezeigt werden. Mehrfach-Umbrüche führen zu Absatzabständen, einfache
     * Umbrüche zu <br/>
     * .
     */
    public static String plainTextToHtml(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty()) {
            return "";
        }
        String normalized = text
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        String[] paragraphs = normalized.split("\\n{2,}", -1);
        StringBuilder sb = new StringBuilder();
        int emitted = 0;
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                continue;
            }
            String trimmedParagraph = paragraph.replaceAll("\\n+$", "");
            if (trimmedParagraph.isEmpty()) {
                continue;
            }
            if (emitted > 0) {
                sb.append("<br/><br/>");
            }
            sb.append("<p>")
                    .append(escapeHtml(trimmedParagraph).replace("\n", "<br/>"))
                    .append("</p>");
            emitted++;
        }
        if (emitted == 0) {
            return "";
        }
        return sb.toString();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static Document prepareDocument(String html) {
        if (html == null) {
            return null;
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String clean = Jsoup.clean(html, "", SAFE_LIST, outputSettings);
        Document doc = Jsoup.parse(clean, "", org.jsoup.parser.Parser.htmlParser());
        doc.outputSettings().prettyPrint(false);

        for (Element pre : doc.select("pre")) {
            String text = pre.text();
            String withBreaks = textToBrHtml(text);
            pre.after(withBreaks);
            pre.unwrap();
        }

        for (Element anchor : doc.select("a[href]")) {
            anchor.attr("target", "_blank");
            anchor.attr("rel", "noopener");
        }
        return doc;
    }

    private static String finalizePreviewHtml(Document doc) {
        String body = doc.body().html();
        body = body.replaceAll("(?i)<br\\s*/?>", "<br/>");
        body = body.replaceAll("(?i)(?:<br/?>\\s*){3,}", "<br/><br/>");
        body = body.replaceAll("(?i)(?:<br/?>\\s*)+$", "");
        return body.trim();
    }

    /**
     * Ersetzt Block-Elemente durch gezielte {@code <br/>
     * }-Einfügepunkte, um Layout f�r Previews zu glätten.
     */
    private static void convertBlocksToBreaks(Document doc, String tag) {
        // Collect elements first to avoid issues with modifying the collection while
        // iterating
        java.util.List<Element> elements = new java.util.ArrayList<>(doc.select(tag));
        // Iterate in reverse to avoid issues with modifying the DOM while iterating
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element el = elements.get(i);
            if (el.parent() == null) {
                continue; // Skip if element has no parent (likely already processed or an orphan).
            }
            // Insert the inner HTML content of the element followed by two <br/> tags
            // before the element.
            // This effectively replaces the block element with its content and a double
            // line break.
            el.before(el.html() + "<br/><br/>");
            // Remove the original element.
            el.remove();
        }
    }

    private static String textToBrHtml(String text) {
        if (text == null || text.isEmpty())
            return "";
        String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return esc.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br/>");
    }
}
