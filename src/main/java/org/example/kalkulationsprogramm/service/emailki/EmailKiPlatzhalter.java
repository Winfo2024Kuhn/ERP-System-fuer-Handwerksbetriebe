package org.example.kalkulationsprogramm.service.emailki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Setzt die Tabellen und Bilder, die die KI nur als Platzhalter gesehen hat, wieder ein.
 *
 * <ul>
 *   <li>Jedes Element kommt genau einmal an die Stelle seines ersten Platzhalters zurück.</li>
 *   <li>Doppelte und erfundene Platzhalter (Nummer nicht vergeben) fallen weg.</li>
 *   <li>Fehlt ein Platzhalter, kommt das Element in Originalreihenfolge ans Ende.</li>
 *   <li>Eine Tabelle mitten in einem Absatz wird hinter den Absatz gesetzt (eine Tabelle darf
 *       nicht in {@code <p>} stehen).</li>
 *   <li>Platzhalter-ähnlicher Text mit fremder Kennung bleibt Text.</li>
 * </ul>
 */
final class EmailKiPlatzhalter {

    private static final Pattern MEHRFACHE_LEERZEICHEN = Pattern.compile("\\h{2,}+");

    private EmailKiPlatzhalter() {
    }

    static void setzeEin(Element body, VorbereiteterEntwurf entwurf) {
        List<String> feste = entwurf.festeElemente();
        boolean[] benutzt = new boolean[feste.size()];
        List<Element> tabellen = new ArrayList<>();
        Set<Element> beruehrt = new LinkedHashSet<>();
        Pattern muster = entwurf.platzhalterMuster();
        for (TextNode text : body.nodeStream(TextNode.class).toList()) {
            if (muster.matcher(text.getWholeText()).find()) {
                beruehrt.add(text.parent() instanceof Element eltern ? eltern : body);
                ersetze(text, muster, feste, benutzt, tabellen);
            }
        }
        Map<Element, Node> letzteEinfuegung = new HashMap<>();
        for (Element tabelle : tabellen) {
            hebeAusAbsatz(tabelle, letzteEinfuegung, beruehrt);
        }
        for (Element element : beruehrt) {
            if (element != body && (element.normalName().equals("p") || element.normalName().equals("div"))) {
                entferneUmbruecheAmEnde(element);
                if (istLeer(element)) {
                    element.remove();
                }
            }
        }
        for (int i = 0; i < feste.size(); i++) {
            if (!benutzt[i]) {
                haengeAn(body, feste.get(i));
            }
        }
    }

    private static void ersetze(TextNode text, Pattern muster, List<String> feste, boolean[] benutzt,
                                List<Element> tabellen) {
        String ganz = text.getWholeText();
        Matcher treffer = muster.matcher(ganz);
        List<Node> neu = new ArrayList<>();
        StringBuilder puffer = new StringBuilder();
        int position = 0;
        while (treffer.find()) {
            puffer.append(ganz, position, treffer.start());
            position = treffer.end();
            int nr = Integer.parseInt(treffer.group(1));
            if (nr >= 1 && nr <= feste.size() && !benutzt[nr - 1]) {
                benutzt[nr - 1] = true;
                leere(puffer, neu);
                neu.addAll(knotenAus(feste.get(nr - 1), tabellen));
            }
        }
        puffer.append(ganz, position, ganz.length());
        leere(puffer, neu);
        for (Node knoten : neu) {
            text.before(knoten);
        }
        text.remove();
    }

    private static void leere(StringBuilder puffer, List<Node> neu) {
        if (!puffer.isEmpty()) {
            neu.add(new TextNode(MEHRFACHE_LEERZEICHEN.matcher(puffer).replaceAll(" ")));
            puffer.setLength(0);
        }
    }

    private static List<Node> knotenAus(String html, List<Element> tabellen) {
        List<Node> knoten = new ArrayList<>(Jsoup.parseBodyFragment(html).body().childNodes());
        for (Node einzeln : knoten) {
            if (einzeln instanceof Element element && element.normalName().equals("table")) {
                tabellen.add(element);
            }
        }
        return knoten;
    }

    private static void hebeAusAbsatz(Element tabelle, Map<Element, Node> letzteEinfuegung, Set<Element> beruehrt) {
        Element absatz = tabelle.parents().stream()
                .filter(eltern -> eltern.normalName().equals("p"))
                .findFirst().orElse(null);
        if (absatz == null) {
            return;
        }
        Node vorher = tabelle.previousSibling();
        Node nachher = tabelle.nextSibling();
        tabelle.remove();
        if (vorher instanceof TextNode links && nachher instanceof TextNode rechts
                && links.getWholeText().endsWith(" ") && rechts.getWholeText().startsWith(" ")) {
            rechts.text(rechts.getWholeText().stripLeading());
        }
        letzteEinfuegung.getOrDefault(absatz, absatz).after(tabelle);
        letzteEinfuegung.put(absatz, tabelle);
        beruehrt.add(absatz);
    }

    private static void entferneUmbruecheAmEnde(Element element) {
        Node letzter = element.lastChild();
        while (letzter != null && (istUmbruch(letzter) || letzter instanceof TextNode text && text.isBlank())) {
            Node davor = letzter.previousSibling();
            letzter.remove();
            letzter = davor;
        }
    }

    private static boolean istUmbruch(Node knoten) {
        return knoten instanceof Element element && element.normalName().equals("br");
    }

    private static boolean istLeer(Element element) {
        return element.text().isBlank() && element.select("img, table").isEmpty();
    }

    private static void haengeAn(Element body, String html) {
        List<Node> knoten = knotenAus(html, new ArrayList<>());
        boolean istTabelle = knoten.stream().anyMatch(k -> k instanceof Element e && e.normalName().equals("table"));
        Element ziel = istTabelle ? body : body.appendElement("p");
        knoten.forEach(ziel::appendChild);
    }
}
