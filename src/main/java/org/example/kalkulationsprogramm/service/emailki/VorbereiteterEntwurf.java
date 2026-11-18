package org.example.kalkulationsprogramm.service.emailki;

import org.jsoup.Jsoup;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ein E-Mail-Entwurf, so wie er an die KI geht, plus alles, was die Nachbereitung braucht.
 *
 * @param htmlFuerKi     bereinigtes HTML des Texts; Tabellen und Bilder stehen darin als
 *                       Platzhalter {@code [[FEST_<kennung>_<nr>]]}
 * @param kennung        zufällige Kennung dieser Anfrage, kommt im Text des Nutzers nicht vor
 * @param festeElemente  bereinigtes HTML der Tabellen und Bilder; Index 0 gehört zu Nummer 1
 * @param erlaubteLinks  Linkadressen der Eingabe; nur diese dürfen in der Antwort Links bleiben
 * @param platzhalterMuster findet die Platzhalter dieser Anfrage, auch mit Leerzeichen innerhalb
 *                       der Klammern; Gruppe 1 ist die Nummer. Wird einmal aus der Kennung gebaut.
 */
public record VorbereiteterEntwurf(String htmlFuerKi, String kennung, List<String> festeElemente,
                                   Set<String> erlaubteLinks, Pattern platzhalterMuster) {

    public VorbereiteterEntwurf {
        festeElemente = List.copyOf(festeElemente);
        erlaubteLinks = Set.copyOf(erlaubteLinks);
    }

    public VorbereiteterEntwurf(String htmlFuerKi, String kennung, List<String> festeElemente,
                                Set<String> erlaubteLinks) {
        this(htmlFuerKi, kennung, festeElemente, erlaubteLinks,
                Pattern.compile("\\[\\[\\h*+FEST_" + Pattern.quote(kennung) + "_(\\d{1,4}+)\\h*+]]"));
    }

    /** Platzhalter für das feste Element mit der Nummer {@code nr} (ab 1). */
    public String platzhalter(int nr) {
        return "[[FEST_" + kennung + "_" + nr + "]]";
    }

    /** {@code true}, wenn außer den Platzhaltern noch Text für die KI übrig ist. */
    public boolean hatText() {
        String text = Jsoup.parseBodyFragment(htmlFuerKi).body().text();
        return !platzhalterMuster().matcher(text).replaceAll("").isBlank();
    }
}
