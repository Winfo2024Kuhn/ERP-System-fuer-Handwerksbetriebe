package org.example.kalkulationsprogramm.service.dokumentstand;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Beschreibt in einfachen Sätzen, was sich zwischen zwei Ständen eines Ausgangsdokuments geändert
 * hat – z. B. „Pos. 3 „Geländer“ gelöscht“, „Rabatt 5 % gegeben“, „Preis Pos. 2: 120,00 € → 95,00 €“.
 *
 * <p>Reine Klasse ohne Spring und Datenbank. Reihenfolge der Sätze: Kopfdaten (Betreff,
 * Zahlungsziel, MwSt, Rechnungsadresse), Bauabschnitte, Positionen, Texte, Reihenfolge,
 * Gesamtrabatt. Höchstens {@value #MAX_SAETZE} Sätze, der Rest wird gezählt; eine geänderte
 * Netto-Summe steht immer als letzte Zeile. Kaputtes JSON führt nie zu einer Ausnahme.</p>
 */
public final class DokumentStandVergleich {

    static final int MAX_SAETZE = 30;
    static final String ERSTE_FASSUNG = "Erste Fassung";

    private DokumentStandVergleich() {
    }

    /**
     * @param vorher  der vorige Stand, {@code null} beim ersten Stand
     * @param nachher der neue Inhalt
     * @return die Sätze; leer, wenn beide Inhalte gleich sind
     */
    public static List<String> aenderungen(DokumentStandInhalt vorher, DokumentStandInhalt nachher) {
        if (vorher == null) {
            return List.of(ERSTE_FASSUNG);
        }
        Objects.requireNonNull(nachher, "nachher");
        List<String> saetze = new ArrayList<>();
        kopfdaten(vorher, nachher, saetze);

        DokumentPositionen alt = DokumentPositionen.lies(vorher.positionenJson());
        DokumentPositionen neu = DokumentPositionen.lies(nachher.positionenJson());
        if (!alt.lesbar() && !neu.lesbar()) {
            if (!Objects.equals(vorher.positionenJson(), nachher.positionenJson())) {
                saetze.add("Positionen geändert");
            }
        } else {
            PositionenVergleich.vergleiche(alt, neu, saetze);
        }

        String summe = summe(vorher.betragNetto(), nachher.betragNetto());
        if (saetze.isEmpty() && summe == null && !vorher.fingerabdruck().equals(nachher.fingerabdruck())) {
            saetze.add("Gestaltung geändert");
        }
        List<String> ergebnis = begrenze(saetze);
        if (summe != null) {
            ergebnis.add(summe);
        }
        return ergebnis;
    }

    private static void kopfdaten(DokumentStandInhalt a, DokumentStandInhalt n, List<String> saetze) {
        String betreffAlt = text(a.betreff());
        String betreffNeu = text(n.betreff());
        if (!betreffAlt.equals(betreffNeu)) {
            saetze.add("Betreff: " + StandTextFormat.zitat(betreffAlt) + " → " + StandTextFormat.zitat(betreffNeu));
        }
        if (!Objects.equals(a.zahlungszielTage(), n.zahlungszielTage())) {
            saetze.add("Zahlungsziel: " + tage(a.zahlungszielTage()) + " → " + tage(n.zahlungszielTage()) + " Tage");
        }
        if (!gleich(a.mwstSatz(), n.mwstSatz())) {
            saetze.add("MwSt.: " + StandTextFormat.mwst(a.mwstSatz()) + " → " + StandTextFormat.mwst(n.mwstSatz()));
        }
        if (!text(a.rechnungsadresseOverride()).equals(text(n.rechnungsadresseOverride()))) {
            saetze.add("Rechnungsadresse geändert");
        }
    }

    private static String summe(BigDecimal alt, BigDecimal neu) {
        if (alt == null || neu == null || alt.compareTo(neu) == 0) {
            return null;
        }
        return "Summe netto: " + StandTextFormat.betrag(alt) + " → " + StandTextFormat.betrag(neu);
    }

    private static List<String> begrenze(List<String> saetze) {
        if (saetze.size() <= MAX_SAETZE) {
            return saetze;
        }
        List<String> kurz = new ArrayList<>(saetze.subList(0, MAX_SAETZE));
        kurz.add("… und " + (saetze.size() - MAX_SAETZE) + " weitere Änderungen");
        return kurz;
    }

    private static boolean gleich(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String tage(Integer tage) {
        return tage == null ? "–" : tage.toString();
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
