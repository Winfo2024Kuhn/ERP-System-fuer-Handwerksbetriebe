package org.example.kalkulationsprogramm.service.emailki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Bereitet einen E-Mail-Entwurf (HTML oder Klartext) für die KI vor.
 *
 * <p>Tabellen und Bilder sieht die KI nie: Sie werden bereinigt aufbewahrt und im Text durch
 * Platzhalter {@code [[FEST_<kennung>_<nr>]]} ersetzt (Tabellen als eigener Absatz, Bilder an
 * ihrer Stelle im Satz). So bleiben Preise und Maße in Tabellen garantiert unverändert. Die
 * Kennung ist je Anfrage zufällig und kommt im Text des Nutzers nicht vor, damit selbst
 * geschriebener Platzhalter-Text nichts durcheinanderbringt. Der übrige Text wird auf die
 * erlaubte Formatierung zurückgeschnitten ({@link EmailKiHtmlBereinigung#bereinigeText}).
 *
 * <p>Gegenstück: {@link EmailKiNachbereitung}.
 */
@Component
public class EmailKiVorbereitung {

    private final RandomGenerator zufall;

    public EmailKiVorbereitung() {
        this(new SecureRandom());
    }

    EmailKiVorbereitung(RandomGenerator zufall) {
        this.zufall = zufall;
    }

    public VorbereiteterEntwurf vorbereiten(String eingabe) {
        String html = EmailKiHtmlBereinigung.istKlartext(eingabe)
                ? EmailKiHtmlBereinigung.textAlsHtml(eingabe)
                : eingabe;
        String kennung = neueKennung(eingabe);
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(EmailKiHtmlBereinigung.ausgabe());
        Set<String> links = linksAus(doc);
        List<String> feste = ersetzeFesteElemente(doc, kennung);
        String htmlFuerKi = EmailKiHtmlBereinigung.bereinigeText(doc.body().html());
        return new VorbereiteterEntwurf(htmlFuerKi, kennung, feste, links);
    }

    private String neueKennung(String eingabe) {
        String kennung;
        do {
            kennung = String.format("%08x", zufall.nextInt());
        } while (eingabe.contains("FEST_" + kennung));
        return kennung;
    }

    /** Linkadressen, wie sie nach der Bereinigung aussehen (gleiche Schreibweise wie in der Antwort). */
    private static Set<String> linksAus(Document doc) {
        Set<String> links = new LinkedHashSet<>();
        String bereinigt = EmailKiHtmlBereinigung.bereinigeFestesElement(doc.body().html());
        for (Element link : Jsoup.parseBodyFragment(bereinigt).select("a[href]")) {
            links.add(link.attr("href"));
        }
        return links;
    }

    private static List<String> ersetzeFesteElemente(Document doc, String kennung) {
        List<String> feste = new ArrayList<>();
        for (Element element : doc.body().select("table, img")) {
            if (element.parents().stream().anyMatch(eltern -> eltern.normalName().equals("table"))) {
                continue; // Bild in einer Tabelle gehört zur Tabelle
            }
            String bereinigt = EmailKiHtmlBereinigung.bereinigeFestesElement(element.outerHtml());
            if (element.normalName().equals("img") && !bereinigt.contains("src=")) {
                element.remove(); // Bild ohne zulässige Quelle: nichts aufzubewahren
                continue;
            }
            feste.add(bereinigt);
            String platzhalter = "[[FEST_" + kennung + "_" + feste.size() + "]]";
            if (element.normalName().equals("table")) {
                element.replaceWith(new Element("p").text(platzhalter));
            } else {
                element.replaceWith(new TextNode(platzhalter));
            }
        }
        return feste;
    }
}
