package org.example.kalkulationsprogramm.util.suche;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.util.HtmlKlartext;

/**
 * Baut den Suchtext einer Mail (Spalte {@code email.suchtext}): alles, was die E-Mail-Suche
 * durchsucht, normalisiert nach {@link EmailSuchNormalisierung} und durch Leerzeichen getrennt.
 *
 * <p>Inhalt in dieser Reihenfolge: Absender (samt Anzeigename, z. B. „Max Mustermann
 * &lt;max@example.com&gt;“), Antwortadresse, Empfänger, CC, Betreff, Dateinamen der Anhänge,
 * Klartext (mit zitiertem Verlauf) und der Text des HTML-Teils. Der HTML-Text kommt nur dazu,
 * wenn er sich vom Klartext unterscheidet. {@code <style>}, {@code <script>} und {@code <head>}
 * zählen nicht (sonst fände „font“ jede HTML-Mail); Entitäten wie {@code &uuml;} werden in einem
 * Durchgang entpackt.</p>
 *
 * <p>Höchstens {@value #MAX_LAENGE} Zeichen; der Rest langer Mails ist nicht durchsuchbar.
 * Die kurzen Felder und Dateinamen stehen vorne und fallen deshalb nie weg.</p>
 */
public final class EmailSuchtext {

    /** Obergrenze des gespeicherten Suchtexts in Zeichen. */
    public static final int MAX_LAENGE = 200_000;
    /** Längere HTML-Teile werden vor dem Entfernen der Tags gekürzt (Laufzeit). */
    private static final int MAX_HTML_LAENGE = 1_000_000;
    private static final List<String> UNSICHTBARE_BLOECKE = List.of("style", "script", "head");

    /** Die Felder einer Mail, aus denen der Suchtext entsteht. */
    public record Quellen(String fromAddress, String replyToAddress, String recipient, String cc, String subject,
            String body, String htmlBody, List<String> dateinamen) {
    }

    private EmailSuchtext() {
    }

    /** Suchtext aus den Feldern; leere Felder fallen weg. */
    public static String aus(Quellen q) {
        List<String> teile = new ArrayList<>();
        teile.add(q.fromAddress());
        teile.add(q.replyToAddress());
        teile.add(q.recipient());
        teile.add(q.cc());
        teile.add(q.subject());
        if (q.dateinamen() != null) {
            teile.addAll(q.dateinamen());
        }
        String body = gekuerzt(EmailSuchNormalisierung.normalisiere(gekuerzt(q.body(), MAX_LAENGE)), MAX_LAENGE);
        StringBuilder sb = new StringBuilder();
        for (String teil : teile) {
            anhaengen(sb, EmailSuchNormalisierung.normalisiere(gekuerzt(teil, MAX_LAENGE)));
        }
        anhaengen(sb, body);
        String html = EmailSuchNormalisierung.normalisiere(gekuerzt(htmlText(q.htmlBody()), MAX_LAENGE));
        if (!html.equals(body)) {
            anhaengen(sb, html);
        }
        return gekuerzt(sb.toString(), MAX_LAENGE);
    }

    /**
     * Trägt einen Dateinamen nach (neuer Anhang an einer schon gespeicherten Mail). Unverändert,
     * wenn er schon drinsteht, kein Platz mehr ist oder es noch keinen Suchtext gibt
     * ({@code null} – den füllt die Nachpflege komplett).
     */
    public static String mitDateiname(String suchtext, String dateiname) {
        String name = EmailSuchNormalisierung.normalisiere(dateiname);
        if (suchtext == null || name.isEmpty() || suchtext.contains(name)) {
            return suchtext;
        }
        String neu = suchtext.isEmpty() ? name : suchtext + " " + name;
        return neu.length() > MAX_LAENGE ? suchtext : neu;
    }

    private static void anhaengen(StringBuilder sb, String teil) {
        if (teil.isEmpty() || sb.length() >= MAX_LAENGE) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(teil);
    }

    /** Sichtbarer Text eines HTML-Teils: unsichtbare Blöcke weg, Tags weg, Entitäten entpackt. */
    static String htmlText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String ohneTags = HtmlKlartext.entferneTags(ohneUnsichtbareBloecke(gekuerzt(html, MAX_HTML_LAENGE)), " ");
        return org.jsoup.parser.Parser.unescapeEntities(ohneTags, false);
    }

    /** Entfernt {@code <style>…</style>} usw. ohne Regex (linear, kein Backtracking). */
    private static String ohneUnsichtbareBloecke(String html) {
        String ergebnis = html;
        for (String tag : UNSICHTBARE_BLOECKE) {
            ergebnis = ohneBlock(ergebnis, tag);
        }
        return ergebnis;
    }

    private static String ohneBlock(String html, String tag) {
        StringBuilder sb = new StringBuilder(html.length());
        int pos = 0;
        while (true) {
            int start = finde(html, "<" + tag, pos);
            if (start < 0) {
                break;
            }
            int ende = finde(html, "</" + tag, start);
            if (ende < 0) {
                break;
            }
            int schluss = html.indexOf('>', ende);
            sb.append(html, pos, start).append(' ');
            pos = schluss < 0 ? html.length() : schluss + 1;
        }
        return sb.append(html, pos, html.length()).toString();
    }

    private static int finde(String text, String suche, int ab) {
        for (int i = ab; i <= text.length() - suche.length(); i++) {
            if (text.charAt(i) == '<' && text.regionMatches(true, i, suche, 0, suche.length())
                    && endetTagName(text, i + suche.length())) {
                return i;
            }
        }
        return -1;
    }

    /** „<head“ darf nicht auf „<header“ passen: danach kommt Leerraum, „>“, „/“ oder das Ende. */
    private static boolean endetTagName(String text, int pos) {
        return pos >= text.length() || !Character.isLetterOrDigit(text.charAt(pos));
    }

    private static String gekuerzt(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
