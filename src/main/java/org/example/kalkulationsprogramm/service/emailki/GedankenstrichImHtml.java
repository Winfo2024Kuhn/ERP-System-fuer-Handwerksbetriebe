package org.example.kalkulationsprogramm.service.emailki;

import org.example.kalkulationsprogramm.util.GedankenstrichBereinigung;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wendet {@link GedankenstrichBereinigung} auf alle Textknoten eines HTML-Baums an. Attribute
 * ({@code href}) bleiben unberührt.
 *
 * <p>Ein Strich steht oft an der Grenze zweier Textknoten ({@code <strong>Preise</strong> – wie}).
 * Deshalb bekommt jeder Knoten das letzte Zeichen seines Vorgängers und das erste seines
 * Nachfolgers in derselben Zeile als Kontext; Absätze, Listenpunkte und {@code <br>} beginnen
 * eine neue Zeile. Leerzeichen neben einem Strich wandern vorher in den Knoten des Strichs,
 * damit der Kontext selbst nie ein Leerzeichen ist.
 */
final class GedankenstrichImHtml {

    private static final Pattern BEGINNT_MIT_STRICH = Pattern.compile("^\\h*+[–—-]");
    private static final Pattern ENDET_MIT_STRICH = Pattern.compile("[–—-]\\h*+$");
    /** Nur am Anfang eines Leerzeichen-Laufs probieren, sonst quadratische Laufzeit (ReDoS). */
    private static final Pattern LEERZEICHEN_AM_ENDE = Pattern.compile("(?<!\\h)\\h++$");
    private static final Pattern LEERZEICHEN_AM_ANFANG = Pattern.compile("^\\h++");
    private static final char ZEILENGRENZE = '\n';

    private GedankenstrichImHtml() {
    }

    static void bereinige(Element wurzel) {
        List<TextNode> knoten = new ArrayList<>();
        List<Boolean> neueZeile = new ArrayList<>();
        sammle(wurzel, knoten, neueZeile);
        verschiebeLeerzeichen(knoten, neueZeile);
        List<String> texte = knoten.stream().map(TextNode::getWholeText).toList();
        for (int i = 0; i < knoten.size(); i++) {
            String text = texte.get(i);
            if (text.indexOf('–') < 0 && text.indexOf('—') < 0 && text.indexOf('-') < 0) {
                continue;
            }
            char davor = neueZeile.get(i) || texte.get(i - 1).isEmpty()
                    ? ZEILENGRENZE : texte.get(i - 1).charAt(texte.get(i - 1).length() - 1);
            boolean letzterDerZeile = i + 1 >= knoten.size() || neueZeile.get(i + 1) || texte.get(i + 1).isEmpty();
            char danach = letzterDerZeile ? ZEILENGRENZE : texte.get(i + 1).charAt(0);
            String bereinigt = GedankenstrichBereinigung.bereinige(davor + text + danach);
            knoten.get(i).text(bereinigt.substring(1, bereinigt.length() - 1));
        }
    }

    private static void sammle(Element wurzel, List<TextNode> knoten, List<Boolean> neueZeile) {
        boolean[] grenze = {true};
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int tiefe) {
                if (node instanceof TextNode text) {
                    knoten.add(text);
                    neueZeile.add(grenze[0]);
                    grenze[0] = false;
                } else if (istZeilengrenze(node)) {
                    grenze[0] = true;
                }
            }

            @Override
            public void tail(Node node, int tiefe) {
                if (istZeilengrenze(node)) {
                    grenze[0] = true;
                }
            }
        }, wurzel);
    }

    private static boolean istZeilengrenze(Node node) {
        return node instanceof Element element && (element.isBlock() || element.normalName().equals("br"));
    }

    private static void verschiebeLeerzeichen(List<TextNode> knoten, List<Boolean> neueZeile) {
        for (int i = 1; i < knoten.size(); i++) {
            if (neueZeile.get(i)) {
                continue;
            }
            TextNode vorher = knoten.get(i - 1);
            TextNode aktuell = knoten.get(i);
            if (BEGINNT_MIT_STRICH.matcher(aktuell.getWholeText()).find()) {
                String leer = treffer(LEERZEICHEN_AM_ENDE, vorher.getWholeText());
                vorher.text(vorher.getWholeText().substring(0, vorher.getWholeText().length() - leer.length()));
                aktuell.text(leer + aktuell.getWholeText());
            } else if (ENDET_MIT_STRICH.matcher(vorher.getWholeText()).find()) {
                String leer = treffer(LEERZEICHEN_AM_ANFANG, aktuell.getWholeText());
                aktuell.text(aktuell.getWholeText().substring(leer.length()));
                vorher.text(vorher.getWholeText() + leer);
            }
        }
    }

    private static String treffer(Pattern muster, String text) {
        var matcher = muster.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }
}
