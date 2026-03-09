package org.example.kalkulationsprogramm.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Werkzeuge zum Ersetzen von cid:-Quellen in HTML durch öffentlich abrufbare URLs der
 * tatsächlichen Inline-Anhänge.
 */
public final class InlineAttachmentUtil {

    private InlineAttachmentUtil() {
    }

    /**
     * Durchsucht HTML nach {@code <img>}-Tags mit {@code cid:}-Referenzen und ersetzt sie anhand
     * der übergebenen Anhänge durch echte Download-Links. Metadaten wie der ursprüngliche
     * Content-ID-Wert werden als Datenattribut hinterlegt.
     */
    public static <T> String rewriteCidSources(String html,
                                               List<T> attachments,
                                               Predicate<T> inlinePredicate,
                                               Function<T, String> contentIdExtractor,
                                               Function<T, String> urlResolver) {
        if (html == null || html.isBlank()) {
            return html;
        }
        if (attachments == null || attachments.isEmpty()) {
            return html;
        }
        Map<String, T> inlineByCid = new HashMap<>();
        for (T attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            if (!inlinePredicate.test(attachment)) {
                continue;
            }
            String cid = contentIdExtractor.apply(attachment);
            if (cid == null || cid.isBlank()) {
                continue;
            }
            String normalized = normalizeContentId(cid);
            inlineByCid.putIfAbsent(normalized.toLowerCase(Locale.ROOT), attachment);
        }
        if (inlineByCid.isEmpty()) {
            return html;
        }
        Document doc = Jsoup.parse(html, "", Parser.htmlParser());
        doc.outputSettings().prettyPrint(false);
        for (Element img : doc.select("img[src^=cid:]")) {
            String raw = img.attr("src");
            if (raw.length() <= 4) {
                continue;
            }
            String normalized = normalizeContentId(raw.substring(4));
            T attachment = inlineByCid.get(normalized.toLowerCase(Locale.ROOT));
            if (attachment != null) {
                String url = urlResolver.apply(attachment);
                if (url != null && !url.isBlank()) {
                    img.attr("src", url);
                    img.attr("data-inline-cid", normalized);
                }
            }
        }
        return doc.body().html();
    }

    /** Vereinheitlicht Content-IDs, indem spitze Klammern und Präfixe entfernt werden. */
    private static String normalizeContentId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("cid:")) {
            trimmed = trimmed.substring(4);
        }
        return trimmed.trim();
    }
}
