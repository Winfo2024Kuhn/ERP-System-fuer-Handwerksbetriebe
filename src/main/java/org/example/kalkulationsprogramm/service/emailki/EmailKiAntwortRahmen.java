package org.example.kalkulationsprogramm.service.emailki;

import org.example.kalkulationsprogramm.util.EmailAiPostProcessor;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Entfernt den Rahmen, den Sprachmodelle gern um eine HTML-Antwort legen: eine Einleitung am
 * Anfang („Hier ist der überarbeitete Text:“) und eine Grußformel samt Namen am Ende
 * („Viele Grüße<br>Max Mustermann“). Die Signatur fügt das Programm selbst an.
 *
 * <p>Gearbeitet wird zeilenweise: Eine Zeile ist ein Absatz der obersten Ebene oder ein
 * durch {@code <br>} getrennter Teil davon. Ob eine Zeile Einleitung oder Gruß ist,
 * entscheiden {@link EmailAiPostProcessor#istEinleitungsZeile} und
 * {@link EmailAiPostProcessor#istGrussZeile}, wie bei Klartext-Antworten. Steht eine
 * Einleitungszeile schon im Entwurf des Nutzers, bleibt sie.
 */
final class EmailKiAntwortRahmen {

    /** Höchstens so viele Zeilen (Name, Firma …) dürfen nach der Grußzeile noch folgen. */
    private static final int ZEILEN_NACH_GRUSS = 4;

    private EmailKiAntwortRahmen() {
    }

    static void entferne(Element body, String entwurfText) {
        List<Zeile> zeilen = zeilen(body);
        Set<Element> geleert = new LinkedHashSet<>();
        String entwurf = entwurfText.toLowerCase(Locale.ROOT);
        for (Zeile zeile : zeilen) {
            String text = zeile.text();
            if (text.isBlank()) {
                continue;
            }
            if (!EmailAiPostProcessor.istEinleitungsZeile(text) || entwurf.contains(text.toLowerCase(Locale.ROOT))) {
                break;
            }
            zeile.entferne(geleert);
        }
        entferneGruss(zeilen, geleert);
        for (Element block : geleert) {
            while (block.firstChild() instanceof Element erstes && erstes.normalName().equals("br")) {
                erstes.remove();
            }
            if (block != body && block.childNodeSize() == 0) {
                block.remove();
            }
        }
    }

    private static void entferneGruss(List<Zeile> zeilen, Set<Element> geleert) {
        int gruss = -1;
        int nichtLeerDanach = 0;
        for (int i = zeilen.size() - 1; i >= 0; i--) {
            String text = zeilen.get(i).text();
            if (EmailAiPostProcessor.istGrussZeile(text)) {
                gruss = i;
                break;
            }
            if (!text.isBlank() && ++nichtLeerDanach > ZEILEN_NACH_GRUSS) {
                return;
            }
        }
        for (int i = Math.max(gruss, 0); gruss >= 0 && i < zeilen.size(); i++) {
            zeilen.get(i).entferne(geleert);
        }
    }

    private static List<Zeile> zeilen(Element body) {
        List<Zeile> zeilen = new ArrayList<>();
        List<Node> lose = new ArrayList<>();
        for (Node knoten : new ArrayList<>(body.childNodes())) {
            if (knoten instanceof Element element && element.isBlock()) {
                zeilen.addAll(teileAnUmbruechen(lose));
                lose = new ArrayList<>();
                zeilen.addAll(element.normalName().equals("p") || element.normalName().equals("div")
                        ? teileAnUmbruechen(element.childNodes())
                        : List.of(new Zeile(List.of(element))));
            } else {
                lose.add(knoten);
            }
        }
        zeilen.addAll(teileAnUmbruechen(lose));
        return zeilen;
    }

    /** Zerlegt Geschwister an {@code <br>}; der Umbruch gehört zur folgenden Zeile. */
    private static List<Zeile> teileAnUmbruechen(List<Node> geschwister) {
        List<Zeile> zeilen = new ArrayList<>();
        List<Node> aktuell = new ArrayList<>();
        for (Node knoten : new ArrayList<>(geschwister)) {
            if (knoten instanceof Element element && element.normalName().equals("br") && !aktuell.isEmpty()) {
                zeilen.add(new Zeile(aktuell));
                aktuell = new ArrayList<>();
            }
            aktuell.add(knoten);
        }
        if (!aktuell.isEmpty()) {
            zeilen.add(new Zeile(aktuell));
        }
        return zeilen;
    }

    private record Zeile(List<Node> knoten) {

        String text() {
            StringBuilder text = new StringBuilder();
            for (Node knoten : knoten) {
                if (knoten instanceof TextNode textKnoten) {
                    text.append(textKnoten.text());
                } else if (knoten instanceof Element element) {
                    text.append(element.text());
                }
            }
            return text.toString().strip();
        }

        void entferne(Set<Element> geleert) {
            for (Node einzeln : knoten) {
                if (einzeln.parent() instanceof Element eltern) {
                    geleert.add(eltern);
                }
                einzeln.remove();
            }
        }
    }
}
