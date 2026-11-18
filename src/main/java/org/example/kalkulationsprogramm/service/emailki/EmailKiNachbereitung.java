package org.example.kalkulationsprogramm.service.emailki;

import org.example.kalkulationsprogramm.util.EmailAiPostProcessor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Set;

/**
 * Macht aus der Antwort der E-Mail-KI das fertige HTML für den Editor.
 *
 * <ol>
 *   <li>Codeblock-Zäune ({@code ```html}) weg; eine Klartext-Antwort wird wie bisher
 *       bereinigt ({@link EmailAiPostProcessor#sanitizePlainText}) und zu Absätzen.</li>
 *   <li>Auf die erlaubte Formatierung zurückschneiden ({@link EmailKiHtmlBereinigung#bereinigeText}).</li>
 *   <li>Links bleiben nur, wenn genau diese Adresse in der Eingabe stand; sonst bleibt nur der
 *       Linktext (Schutz gegen eingeschleuste Links).</li>
 *   <li>Einleitung und Grußformel der KI entfernen ({@link EmailKiAntwortRahmen}).</li>
 *   <li>Gedankenstriche als Satzzeichen ersetzen ({@link GedankenstrichImHtml}), nur im Text.</li>
 *   <li>Tabellen und Bilder wieder einsetzen ({@link EmailKiPlatzhalter}), unverändert.</li>
 * </ol>
 *
 * Bleibt außer Platzhaltern kein Text übrig, ist das Ergebnis leer („kein Vorschlag“).
 */
public final class EmailKiNachbereitung {

    private EmailKiNachbereitung() {
    }

    public static String fertigstellen(String kiAntwort, VorbereiteterEntwurf entwurf) {
        if (kiAntwort == null || kiAntwort.isBlank()) {
            return "";
        }
        String roh = EmailAiPostProcessor.stripCodeFences(
                kiAntwort.replace("\r\n", "\n").replace('\r', '\n').strip()).strip();
        String html = EmailKiHtmlBereinigung.istKlartext(roh)
                ? EmailKiHtmlBereinigung.textAlsHtml(EmailAiPostProcessor.sanitizePlainText(roh))
                : roh;
        Document doc = dokument(EmailKiHtmlBereinigung.bereinigeText(html));
        entferneFremdeLinks(doc.body(), entwurf.erlaubteLinks());
        EmailKiAntwortRahmen.entferne(doc.body(), dokument(entwurf.htmlFuerKi()).body().text());
        if (entwurf.platzhalterMuster().matcher(doc.body().text()).replaceAll("").isBlank()) {
            return "";
        }
        GedankenstrichImHtml.bereinige(doc.body());
        EmailKiPlatzhalter.setzeEin(doc.body(), entwurf);
        return doc.body().html().strip();
    }

    /** Für abgeschaltete KI: der bereinigte Entwurf mit seinen festen Elementen, sonst unverändert. */
    public static String ohneKi(VorbereiteterEntwurf entwurf) {
        Document doc = dokument(entwurf.htmlFuerKi());
        EmailKiPlatzhalter.setzeEin(doc.body(), entwurf);
        return doc.body().html().strip();
    }

    private static void entferneFremdeLinks(Element body, Set<String> erlaubt) {
        for (Element link : body.select("a")) {
            if (!erlaubt.contains(link.attr("href"))) {
                link.unwrap();
            }
        }
    }

    private static Document dokument(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(EmailKiHtmlBereinigung.ausgabe());
        return doc;
    }
}
