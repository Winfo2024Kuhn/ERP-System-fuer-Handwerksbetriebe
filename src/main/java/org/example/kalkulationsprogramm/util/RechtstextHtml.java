package org.example.kalkulationsprogramm.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Bereinigt das HTML bearbeitbarer Rechtstexte auf eine kleine Positivliste. Der Editor im
 * Formularwesen kennt nur Absatz, Zeilenumbruch, Fett, Kursiv, Überschrift und Listen – alles
 * andere (Skripte, Attribute, Bilder, Links, Stile) fällt beim Speichern weg.
 */
public final class RechtstextHtml
{
    /** Erlaubte Tags, ohne jedes Attribut. */
    static final String[] ERLAUBTE_TAGS = { "p", "br", "strong", "b", "em", "i", "h3", "ul", "ol", "li" };

    private static final Safelist POSITIVLISTE = Safelist.none().addTags(ERLAUBTE_TAGS);
    private static final Document.OutputSettings AUSGABE = new Document.OutputSettings().prettyPrint(false);

    private RechtstextHtml()
    {
    }

    /** Bereinigtes HTML; {@code null} wird zu {@code ""}. */
    public static String bereinige(String html)
    {
        if (html == null) return "";
        return Jsoup.clean(html, "", POSITIVLISTE, AUSGABE).trim();
    }

    /** {@code true}, wenn nach dem Entfernen aller Tags kein lesbarer Text bleibt. */
    public static boolean istLeer(String html)
    {
        return html == null || HtmlKlartext.zuText(html).isBlank();
    }
}
