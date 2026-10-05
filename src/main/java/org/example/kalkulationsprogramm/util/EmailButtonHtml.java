package org.example.kalkulationsprogramm.util;

import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Hilfsfunktionen fuer Buttons in E-Mail-Vorlagen.
 *
 * <p>Der Vorlagen-Editor speichert einen Button als
 * {@code <table data-email-button>…<a href="…">Text</a>…</table>}. Das Ziel ist
 * entweder eine feste Adresse oder der Platzhalter {@code {{REVIEW_URL}}}, der
 * beim Versand durch den Google-Bewertungs-Link der Firma ersetzt wird.</p>
 */
public final class EmailButtonHtml {

    static final String BUTTON_MARKER = "data-email-button";

    private EmailButtonHtml() {
    }

    /**
     * Entfernt nach dem Ersetzen der Platzhalter alle Buttons, deren Ziel leer
     * oder nicht klickbar ist. Typischer Fall: In den Firmendaten ist kein
     * Bewertungs-Link hinterlegt — dann soll der Kunde keinen Button sehen, der
     * ins Leere fuehrt. Gleichzeitig faengt das {@code javascript:}-Ziele ab.
     *
     * <p>HTML ohne Button wird unveraendert zurueckgegeben, damit Vorlagen ohne
     * Button nicht durch einen Parse-/Serialisier-Durchlauf veraendert werden.</p>
     */
    public static String entferneButtonsOhneZiel(String html) {
        if (html == null || !html.contains(BUTTON_MARKER)) {
            return html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        boolean geaendert = false;
        for (Element button : doc.select("table[" + BUTTON_MARKER + "]")) {
            Element link = button.selectFirst("a[href]");
            if (link == null || !istKlickbaresZiel(link.attr("href"))) {
                button.remove();
                geaendert = true;
            }
        }
        return geaendert ? doc.body().html() : html;
    }

    /**
     * Liefert die Adresse so, dass sie gefahrlos in ein {@code href="…"} passt,
     * oder einen leeren String, wenn sie kein http(s)-Link ist.
     */
    public static String alsSichereAdresse(String url) {
        if (url == null) {
            return "";
        }
        String wert = url.trim();
        if (!istWebAdresse(wert) || wert.chars().anyMatch(Character::isWhitespace)) {
            return "";
        }
        // Zeichen, die das Attribut oder das umgebende HTML sprengen koennten.
        return wert.replace("\"", "%22").replace("<", "%3C").replace(">", "%3E");
    }

    private static boolean istKlickbaresZiel(String href) {
        String wert = href.trim().toLowerCase(Locale.ROOT);
        return istWebAdresse(wert) || wert.startsWith("mailto:") || wert.startsWith("tel:");
    }

    private static boolean istWebAdresse(String wert) {
        String klein = wert.toLowerCase(Locale.ROOT);
        return (klein.startsWith("https://") && klein.length() > "https://".length())
                || (klein.startsWith("http://") && klein.length() > "http://".length());
    }
}
