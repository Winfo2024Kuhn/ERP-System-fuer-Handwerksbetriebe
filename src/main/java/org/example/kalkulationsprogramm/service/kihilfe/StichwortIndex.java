package org.example.kalkulationsprogramm.service.kihilfe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Wortsuche (BM25) ueber die Abschnitte der Wissensbasis - die zweite Haelfte der Hybrid-Suche.
 * <p>
 * Die Vektor-Suche findet Bedeutungen ("Rechnung rueckgaengig machen" ~ Storno). Woertliche
 * Begriffe aus der Oberflaeche ("Schlussrechnung", "Stornieren") verfehlt sie bei Code aber oft;
 * die findet diese Suche. Ein Suchwort ab {@value #MIN_PRAEFIX} Buchstaben findet auch seine
 * Verlaengerungen: "Rechnungen" -> Stamm "rechnung" -> rechnung, rechnungen, rechnungsnummer.
 * <p>
 * Ohne regulaere Ausdruecke: Die Suchwoerter kommen aus dem Chat (ReDoS).
 */
public final class StichwortIndex {

    /** Uebliche BM25-Werte: Saettigung der Worthaeufigkeit und Einfluss der Abschnittslaenge. */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** Kuerzere Staemme muessen genau passen - sonst findet "ab" alles mit "ab...". */
    static final int MIN_PRAEFIX = 4;
    /** Mehr Woerter je Anfrage bringen nichts, kosten aber Rechenzeit. */
    static final int MAX_SUCHWOERTER = 24;
    /** Laengere "Woerter" sind Base64, Hashes oder Minifiziertes - kein Suchbegriff. */
    private static final int MAX_WORTLAENGE = 40;
    /**
     * Ein Treffer im Titel (Dateipfad, Abschnittsname) zaehlt dreifach: Bei "Lieferant anlegen" soll
     * die Seite LieferantenEditor vor langen Texten liegen, die das Wort nur oft erwaehnen.
     */
    static final int TITEL_GEWICHT = 3;

    /** Endungen, die der Stamm eines Suchworts verliert; laengste zuerst. */
    private static final List<String> ENDUNGEN = List.of("innen", "ern", "en", "er", "es", "em", "e", "s");

    /** Fuellwoerter aus Fragen und Anleitungen - sie unterscheiden keine Abschnitte. */
    private static final Set<String> FUELLWOERTER = Set.of(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
            "und", "oder", "aber", "wie", "was", "wo", "wer", "wann", "warum", "wieso", "welche", "welcher",
            "ich", "du", "er", "sie", "es", "wir", "ihr", "man", "mich", "mir", "dich", "dir", "uns",
            "mein", "meine", "meinen", "dein", "deine", "sein", "seine", "ihre", "ihren",
            "mit", "von", "vom", "zu", "zum", "zur", "im", "in", "am", "an", "auf", "aus", "bei", "fuer",
            "ist", "sind", "war", "wird", "werden", "kann", "kannst", "koennen", "muss", "musst", "soll",
            "nicht", "kein", "keine", "noch", "nur", "auch", "so", "dann", "wenn", "ob", "als", "bis",
            "nach", "vor", "ueber", "unter", "durch", "hier", "da", "dort", "gibt", "geht", "bitte",
            "the", "and", "for", "to", "of", "is");

    /** Wortschatz alphabetisch - so liegen alle Verlaengerungen eines Stamms nebeneinander. */
    private final String[] woerter;
    /** Je Wort: in welchen Abschnitten (Positionen) es vorkommt und wie oft im Titel bzw. Inhalt. */
    private final int[][] fundstellen;
    private final int[][] imTitel;
    private final int[][] imInhalt;
    private final Feldlaengen titel;
    private final Feldlaengen inhalt;

    /** Ein Treffer: Position des Abschnitts in der Liste, aus der der Index gebaut wurde. */
    public record Treffer(int position, double punkte) {}

    /** Ein Abschnitt fuer den Index: Titel (Dateipfad, Name) und Inhalt. */
    public record Text(String titel, String inhalt) {}

    /** Wortanzahl je Abschnitt in einem Feld und ihr Mittel - fuer die Laengen-Normierung. */
    private record Feldlaengen(int[] laengen, double mittel) {

        static Feldlaengen aus(int[] laengen) {
            long summe = 0;
            for (int laenge : laengen) summe += laenge;
            return new Feldlaengen(laengen, laengen.length == 0 ? 1 : Math.max(1.0, (double) summe / laengen.length));
        }

        /** Haeufigkeit geteilt durch den Laengenfaktor: lange Abschnitte zaehlen je Treffer weniger. */
        double normiert(int haeufigkeit, int position) {
            return haeufigkeit == 0 ? 0 : haeufigkeit / (1 - B + B * laengen[position] / mittel);
        }
    }

    private StichwortIndex(String[] woerter, int[][] fundstellen, int[][] imTitel, int[][] imInhalt,
                           int[] titelLaengen, int[] inhaltLaengen) {
        this.woerter = woerter;
        this.fundstellen = fundstellen;
        this.imTitel = imTitel;
        this.imInhalt = imInhalt;
        this.titel = Feldlaengen.aus(titelLaengen);
        this.inhalt = Feldlaengen.aus(inhaltLaengen);
    }

    /** Baut den Index; Abschnitt i der Liste hat im Ergebnis die Position i. */
    public static StichwortIndex aus(List<Text> texte) {
        Map<String, Postliste> wortschatz = new HashMap<>();
        int[] titelLaengen = new int[texte.size()];
        int[] inhaltLaengen = new int[texte.size()];
        for (int position = 0; position < texte.size(); position++) {
            Map<String, int[]> zaehler = new HashMap<>();
            List<String> titelWoerter = woerter(texte.get(position).titel());
            List<String> inhaltWoerter = woerter(texte.get(position).inhalt());
            for (String wort : titelWoerter) zaehler.computeIfAbsent(wort, k -> new int[2])[0]++;
            for (String wort : inhaltWoerter) zaehler.computeIfAbsent(wort, k -> new int[2])[1]++;
            titelLaengen[position] = titelWoerter.size();
            inhaltLaengen[position] = inhaltWoerter.size();
            for (Map.Entry<String, int[]> e : zaehler.entrySet()) {
                wortschatz.computeIfAbsent(e.getKey(), k -> new Postliste()).add(position, e.getValue());
            }
        }
        String[] sortiert = wortschatz.keySet().toArray(String[]::new);
        Arrays.sort(sortiert);
        int[][] fundstellen = new int[sortiert.length][];
        int[][] imTitel = new int[sortiert.length][];
        int[][] imInhalt = new int[sortiert.length][];
        for (int i = 0; i < sortiert.length; i++) {
            Postliste liste = wortschatz.get(sortiert[i]);
            fundstellen[i] = Arrays.copyOf(liste.positionen, liste.anzahl);
            imTitel[i] = Arrays.copyOf(liste.imTitel, liste.anzahl);
            imInhalt[i] = Arrays.copyOf(liste.imInhalt, liste.anzahl);
        }
        return new StichwortIndex(sortiert, fundstellen, imTitel, imInhalt, titelLaengen, inhaltLaengen);
    }

    /** Die {@code anzahl} besten Abschnitte zur Anfrage, beste zuerst; leer ohne brauchbares Suchwort. */
    public List<Treffer> suchen(String anfrage, int anzahl) {
        return suchen(anfrage, anzahl, position -> true);
    }

    /**
     * Wie {@link #suchen(String, int)}, aber nur unter den Abschnitten, die {@code erlaubt} zulaesst.
     * Gewertet wird nach BM25F: Titel und Inhalt je fuer sich laengennormiert, der Titel
     * {@value #TITEL_GEWICHT}-fach, dann gemeinsam gesaettigt.
     */
    public List<Treffer> suchen(String anfrage, int anzahl, IntPredicate erlaubt) {
        int abschnitte = fundstellenAnzahl();
        if (anfrage == null || anzahl <= 0 || abschnitte == 0) return List.of();
        double[] punkte = new double[abschnitte];
        boolean getroffen = false;
        for (String stamm : suchstaemme(anfrage)) {
            int[] tfTitel = new int[abschnitte];
            int[] tfInhalt = new int[abschnitte];
            boolean[] enthalten = new boolean[abschnitte];
            int df = 0;
            for (int wort : passendeWoerter(stamm)) {
                int[] stellen = fundstellen[wort];
                for (int i = 0; i < stellen.length; i++) {
                    if (!enthalten[stellen[i]]) {
                        enthalten[stellen[i]] = true;
                        df++;
                    }
                    tfTitel[stellen[i]] += imTitel[wort][i];
                    tfInhalt[stellen[i]] += imInhalt[wort][i];
                }
            }
            if (df == 0) continue;
            getroffen = true;
            double idf = Math.log(1 + (abschnitte - df + 0.5) / (df + 0.5));
            for (int position = 0; position < abschnitte; position++) {
                if (!enthalten[position]) continue;
                double gewicht = TITEL_GEWICHT * titel.normiert(tfTitel[position], position)
                        + inhalt.normiert(tfInhalt[position], position);
                punkte[position] += idf * gewicht * (K1 + 1) / (gewicht + K1);
            }
        }
        if (!getroffen) return List.of();

        List<Treffer> treffer = new ArrayList<>();
        for (int position = 0; position < abschnitte; position++) {
            if (punkte[position] > 0 && erlaubt.test(position)) treffer.add(new Treffer(position, punkte[position]));
        }
        treffer.sort(Comparator.comparingDouble(Treffer::punkte).reversed().thenComparingInt(Treffer::position));
        return treffer.size() <= anzahl ? treffer : List.copyOf(treffer.subList(0, anzahl));
    }

    private int fundstellenAnzahl() {
        return inhalt.laengen().length;
    }

    /** Staemme der Suchwoerter, ohne Doppelte, hoechstens {@value #MAX_SUCHWOERTER}. */
    static List<String> suchstaemme(String anfrage) {
        Set<String> staemme = new LinkedHashSet<>();
        for (String wort : woerter(anfrage)) {
            staemme.add(stamm(wort));
            if (staemme.size() == MAX_SUCHWOERTER) break;
        }
        return List.copyOf(staemme);
    }

    /** Woerter, die zum Stamm passen: genau gleich, ab {@value #MIN_PRAEFIX} Buchstaben auch laenger. */
    private List<Integer> passendeWoerter(String stamm) {
        int start = Arrays.binarySearch(woerter, stamm);
        if (stamm.length() < MIN_PRAEFIX) {
            return start >= 0 ? List.of(start) : List.of();
        }
        List<Integer> passend = new ArrayList<>();
        for (int i = start >= 0 ? start : -start - 1; i < woerter.length && woerter[i].startsWith(stamm); i++) {
            passend.add(i);
        }
        return passend;
    }

    /** Wortstamm fuer die Suche: "Rechnungen" -> "rechnung", "storniere" -> "stornier". */
    static String stamm(String wort) {
        for (String endung : ENDUNGEN) {
            if (wort.endsWith(endung) && wort.length() - endung.length() >= MIN_PRAEFIX) {
                return wort.substring(0, wort.length() - endung.length());
            }
        }
        return wort;
    }

    /**
     * Zerlegt Text in Suchwoerter: klein, Umlaute ausgeschrieben (der Code schreibt "Rechnungsuebersicht"),
     * CamelCase getrennt ("ProjektEditor" -> projekt, editor), ohne Fuellwoerter.
     */
    static List<String> woerter(String text) {
        List<String> woerter = new ArrayList<>();
        if (text == null) return woerter;
        StringBuilder wort = new StringBuilder();
        char vorher = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                wortAbschliessen(woerter, wort);
            } else {
                // "projektEditor" und "PDFExport": neues Wort bei klein->GROSS bzw. GROSS->Gross
                boolean neuesWort = Character.isUpperCase(c) && (Character.isLowerCase(vorher)
                        || (Character.isUpperCase(vorher) && i + 1 < text.length()
                        && Character.isLowerCase(text.charAt(i + 1))));
                if (neuesWort) wortAbschliessen(woerter, wort);
                anhaengen(wort, Character.toLowerCase(c));
            }
            vorher = c;
        }
        wortAbschliessen(woerter, wort);
        return woerter;
    }

    private static void anhaengen(StringBuilder wort, char c) {
        switch (c) {
            case 'ä' -> wort.append("ae");
            case 'ö' -> wort.append("oe");
            case 'ü' -> wort.append("ue");
            case 'ß' -> wort.append("ss");
            default -> wort.append(c);
        }
    }

    private static void wortAbschliessen(List<String> woerter, StringBuilder wort) {
        if (wort.length() >= 2 && wort.length() <= MAX_WORTLAENGE) {
            String fertig = wort.toString().toLowerCase(Locale.ROOT);
            if (!FUELLWOERTER.contains(fertig)) woerter.add(fertig);
        }
        wort.setLength(0);
    }

    /** Wachsende Liste ohne Boxing - der Index hat Hunderttausende Eintraege. */
    private static final class Postliste {
        private int[] positionen = new int[4];
        private int[] imTitel = new int[4];
        private int[] imInhalt = new int[4];
        private int anzahl;

        /** {@code haeufigkeit} = {im Titel, im Inhalt}. */
        void add(int position, int[] haeufigkeit) {
            if (anzahl == positionen.length) {
                positionen = Arrays.copyOf(positionen, anzahl * 2);
                imTitel = Arrays.copyOf(imTitel, anzahl * 2);
                imInhalt = Arrays.copyOf(imInhalt, anzahl * 2);
            }
            positionen[anzahl] = position;
            imTitel[anzahl] = haeufigkeit[0];
            imInhalt[anzahl] = haeufigkeit[1];
            anzahl++;
        }
    }
}
