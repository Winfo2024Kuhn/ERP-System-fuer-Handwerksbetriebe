package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.PositionsArt;

/**
 * Vergleicht die Positionen zweier Lieferanten-Dokumente für die
 * Dokumentenkette.
 *
 * <p>Zwischen Angebot und AB fallen Positionen weg oder kommen dazu, ein
 * Lieferschein deckt bei Teillieferungen nur einen Teil der AB ab. Deshalb
 * zählt der Anteil gleicher Positionen am <b>kleineren</b> Dokument – nicht,
 * ob beide Listen identisch sind.
 *
 * <p>Zwei Positionen gelten als gleich, wenn die Artikelnummer übereinstimmt
 * oder – ohne Nummer – die Bezeichnungen überwiegend dieselben Wörter haben.
 * Nebenkosten und Rabatte zählen nicht mit: "Fracht" steht auf fast jedem Beleg.
 */
final class PositionsVergleich {

    /** Ab diesem Wortanteil (Jaccard) gelten zwei Bezeichnungen als gleich. */
    static final double MIN_WORT_AEHNLICHKEIT = 0.6;
    /** Mindestanteil gleicher Positionen am kleineren Dokument. */
    static final double MIN_ANTEIL = 0.6;
    /** Ab diesem Anteil (und {@link #MIN_TREFFER_STARK} Treffern) zählt die Übereinstimmung als stark. */
    static final double ANTEIL_STARK = 0.8;
    static final int MIN_TREFFER_STARK = 3;
    /** Mehr Warenpositionen je Beleg vergleichen wir nicht (Paarvergleich ist n·m). */
    static final int MAX_POSITIONEN = 300;

    private static final Pattern NICHT_WORT = Pattern.compile("[^\\p{L}\\p{N}]++");
    private static final Pattern AKZENTE = Pattern.compile("\\p{M}++");
    private static final Set<String> FUELLWOERTER = Set.of(
            "und", "mit", "fuer", "für", "der", "die", "das", "aus", "inkl", "lt", "gem", "nach", "per", "pro",
            "stk", "stueck", "stück", "st");

    private PositionsVergleich() {
    }

    /**
     * Ergebnis eines Vergleichs.
     *
     * @param gleich       Anzahl gleicher Warenpositionen
     * @param kleinere     Warenpositionen des kleineren Dokuments
     * @param gleicheMenge davon mit gleicher Menge
     */
    record Ergebnis(int gleich, int kleinere, int gleicheMenge) {
        static final Ergebnis KEINS = new Ergebnis(0, 0, 0);

        double anteil() {
            return kleinere == 0 ? 0 : (double) gleich / kleinere;
        }

        /** Genug gleiche Positionen, um als Hinweis zu zählen. */
        boolean passt() {
            return gleich > 0 && anteil() >= MIN_ANTEIL;
        }

        boolean stark() {
            return gleich >= MIN_TREFFER_STARK && anteil() >= ANTEIL_STARK;
        }

        /** Mindestens die Hälfte der gleichen Positionen hat auch die gleiche Menge. */
        boolean mengenPassen() {
            return gleich >= 2 && gleicheMenge * 2 >= gleich;
        }
    }

    /** Eine Position, für den Vergleich vorbereitet. */
    record Merkmal(String artikelnummer, Set<String> woerter, BigDecimal menge) {
    }

    /** Bereitet die Warenpositionen eines Dokuments vor; Nebenkosten/Rabatte fallen heraus. */
    static List<Merkmal> merkmale(List<AusgelesenePosition> positionen) {
        if (positionen == null || positionen.isEmpty()) {
            return List.of();
        }
        List<Merkmal> ergebnis = new ArrayList<>();
        for (AusgelesenePosition p : positionen) {
            if (ergebnis.size() >= MAX_POSITIONEN) {
                break;
            }
            if (p == null || (p.positionsArt() != null && p.positionsArt() != PositionsArt.WARE)) {
                continue;
            }
            String nummer = LieferantDokumentAbgleich.normalisiereNummer(p.externeArtikelnummer());
            Set<String> woerter = woerter(p.bezeichnung());
            if (nummer == null && woerter.size() < 2) {
                continue; // weder Nummer noch aussagekräftiger Text
            }
            ergebnis.add(new Merkmal(nummer, woerter, p.menge()));
        }
        return ergebnis;
    }

    /**
     * Vergleicht zwei vorbereitete Positionslisten. Jede Position wird höchstens
     * einmal zugeordnet (gierig: zuerst Artikelnummern, dann Bezeichnungen).
     */
    static Ergebnis vergleiche(List<Merkmal> a, List<Merkmal> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return Ergebnis.KEINS;
        }
        List<Merkmal> klein = a.size() <= b.size() ? a : b;
        List<Merkmal> gross = klein == a ? b : a;
        boolean[] vergeben = new boolean[gross.size()];
        int gleich = 0;
        int gleicheMenge = 0;
        boolean[] gefunden = new boolean[klein.size()];

        // 1. Durchgang: gleiche Artikelnummer
        for (int i = 0; i < klein.size(); i++) {
            Merkmal m = klein.get(i);
            if (m.artikelnummer() == null) {
                continue;
            }
            for (int j = 0; j < gross.size(); j++) {
                if (!vergeben[j] && m.artikelnummer().equals(gross.get(j).artikelnummer())) {
                    vergeben[j] = true;
                    gefunden[i] = true;
                    gleich++;
                    gleicheMenge += gleicheMenge(m, gross.get(j)) ? 1 : 0;
                    break;
                }
            }
        }
        // 2. Durchgang: ähnliche Bezeichnung (bester freier Kandidat)
        for (int i = 0; i < klein.size(); i++) {
            if (gefunden[i]) {
                continue;
            }
            Merkmal m = klein.get(i);
            int bester = -1;
            double besteAehnlichkeit = MIN_WORT_AEHNLICHKEIT;
            for (int j = 0; j < gross.size(); j++) {
                if (vergeben[j] || widersprechendeNummern(m, gross.get(j))) {
                    continue;
                }
                double aehnlichkeit = aehnlichkeit(m.woerter(), gross.get(j).woerter());
                if (aehnlichkeit >= besteAehnlichkeit) {
                    besteAehnlichkeit = aehnlichkeit;
                    bester = j;
                }
            }
            if (bester >= 0) {
                vergeben[bester] = true;
                gleich++;
                gleicheMenge += gleicheMenge(m, gross.get(bester)) ? 1 : 0;
            }
        }
        return new Ergebnis(gleich, klein.size(), gleicheMenge);
    }

    /** Beide tragen eine Artikelnummer, aber verschiedene – dann hilft auch der gleiche Text nicht. */
    private static boolean widersprechendeNummern(Merkmal a, Merkmal b) {
        return a.artikelnummer() != null && b.artikelnummer() != null
                && !a.artikelnummer().equals(b.artikelnummer());
    }

    private static boolean gleicheMenge(Merkmal a, Merkmal b) {
        return a.menge() != null && b.menge() != null && a.menge().signum() != 0
                && a.menge().abs().compareTo(b.menge().abs()) == 0;
    }

    /** Jaccard-Ähnlichkeit zweier Wortmengen. */
    static double aehnlichkeit(Set<String> a, Set<String> b) {
        if (a.size() < 2 || b.size() < 2) {
            return 0;
        }
        Set<String> schnitt = new HashSet<>(a);
        schnitt.retainAll(b);
        if (schnitt.isEmpty()) {
            return 0;
        }
        Set<String> vereinigung = new HashSet<>(a);
        vereinigung.addAll(b);
        return (double) schnitt.size() / vereinigung.size();
    }

    /**
     * Wörter einer Bezeichnung: klein, ohne Akzente/Satzzeichen, ohne
     * Füllwörter. Maße wie "50x5" bleiben ein Wort.
     */
    static Set<String> woerter(String bezeichnung) {
        if (bezeichnung == null || bezeichnung.isBlank()) {
            return Set.of();
        }
        String text = bezeichnung.length() > 500 ? bezeichnung.substring(0, 500) : bezeichnung;
        text = text.toLowerCase(Locale.GERMAN).replace("ß", "ss")
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue");
        text = AKZENTE.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        Set<String> woerter = new HashSet<>();
        for (String wort : NICHT_WORT.split(text)) {
            if (wort.length() >= 2 && !FUELLWOERTER.contains(wort)) {
                woerter.add(wort);
            }
        }
        return woerter;
    }
}
