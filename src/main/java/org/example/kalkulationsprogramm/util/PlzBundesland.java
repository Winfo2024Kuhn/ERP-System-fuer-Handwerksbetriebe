package org.example.kalkulationsprogramm.util;

import org.example.kalkulationsprogramm.domain.Bundesland;

import java.util.List;
import java.util.Optional;

import static org.example.kalkulationsprogramm.domain.Bundesland.*;

/**
 * Schlägt aus einer deutschen Postleitzahl das Bundesland vor (Issue #222: die
 * Firmenadresse soll das Bundesland für die Feiertage gleich mitliefern).
 * <p>
 * Postleitzahl-Gebiete folgen nicht genau den Landesgrenzen. Die Tabelle kennt die
 * zweistelligen Leitregionen und die bekannten Grenzgebiete, die in ein anderes Land
 * fallen. Das Ergebnis ist deshalb nur ein <b>Vorschlag</b>, den der Nutzer bestätigt.
 * Keine Netzabfrage, die Adresse verlässt den Rechner nicht.
 */
public final class PlzBundesland {

    private record Bereich(int von, int bis, Bundesland land) {
    }

    /**
     * Reihenfolge zählt: Grenzgebiete zuerst, danach die Leitregionen. Der erste
     * passende Bereich gewinnt.
     */
    private static final List<Bereich> BEREICHE = List.of(
            // --- Grenzgebiete (abweichend von der Leitregion) ---
            new Bereich(1945, 1998, BB),     // Ruhland, Senftenberg, Lauchhammer
            new Bereich(4600, 4639, TH),     // Altenburger Land
            new Bereich(4895, 4938, BB),     // Falkenberg, Elbe-Elster
            new Bereich(6556, 6578, TH),     // Artern, Bad Frankenhausen
            new Bereich(7919, 7985, SN),     // Mühltroff, Pausa, Elsterberg
            new Bereich(14050, 14199, BE),   // Berlin-Charlottenburg bis Zehlendorf
            new Bereich(17268, 17291, BB),   // Templin, Lychen, Prenzlau
            new Bereich(17326, 17326, BB),   // Brüssow
            new Bereich(17337, 17337, BB),   // Uckerland
            new Bereich(19309, 19357, BB),   // Lenzen, Wittenberge, Perleberg
            new Bereich(21000, 21149, HH),   // Hamburg-Bergedorf, Harburg
            new Bereich(21522, 21522, NI),   // Hohnstorf (Elbe)
            new Bereich(21481, 21529, SH),   // Lauenburg, Geesthacht, Glinde
            new Bereich(22844, 22969, SH),   // Norderstedt, Wedel, Ahrensburg
            new Bereich(23923, 23999, MV),   // Schönberg, Wismar
            new Bereich(27568, 27580, HB),   // Bremerhaven
            new Bereich(28790, 28879, NI),   // Schwanewede, Stuhr, Achim, Syke
            new Bereich(34346, 34355, NI),   // Hann. Münden, Staufenberg
            new Bereich(34414, 34439, NW),   // Warburg, Borgentreich
            new Bereich(36404, 36469, TH),   // Bad Salzungen, Wartburgregion
            new Bereich(37213, 37299, HE),   // Witzenhausen, Eschwege
            new Bereich(37308, 37359, TH),   // Eichsfeld
            new Bereich(37671, 37699, NW),   // Höxter, Beverungen
            new Bereich(38486, 38489, ST),   // Klötze
            new Bereich(38820, 38899, ST),   // Halberstadt, Wernigerode
            new Bereich(48455, 48531, NI),   // Bad Bentheim, Nordhorn
            new Bereich(49477, 49549, NW),   // Ibbenbüren, Tecklenburg
            new Bereich(53424, 53579, RP),   // Remagen, Ahrweiler, Linz
            new Bereich(57518, 57648, RP),   // Betzdorf, Altenkirchen, Hachenburg
            new Bereich(63739, 63939, BY),   // Aschaffenburg, Miltenberg
            new Bereich(66482, 66509, RP),   // Zweibrücken
            new Bereich(66849, 66999, RP),   // Landstuhl, Kusel, Pirmasens (66822 Lebach bleibt SL)
            new Bereich(68519, 68519, HE),   // Viernheim
            new Bereich(68623, 68649, HE),   // Lampertheim, Bürstadt, Biblis
            new Bereich(69239, 69239, HE),   // Neckarsteinach
            new Bereich(69434, 69434, HE),   // Hirschhorn
            new Bereich(69483, 69518, HE),   // Wald-Michelbach, Birkenau
            new Bereich(76726, 76891, RP),   // Germersheim, Landau, Bad Bergzabern
            new Bereich(88100, 88199, BY),   // Lindau, Lindenberg
            new Bereich(89200, 89499, BY),   // Neu-Ulm, Günzburg, Dillingen
            new Bereich(96500, 96599, TH),   // Sonneberg
            new Bereich(97877, 97999, BW),   // Wertheim, Tauberbischofsheim, Bad Mergentheim

            // --- Leitregionen ---
            new Bereich(1000, 2999, SN),
            new Bereich(3000, 3999, BB),
            new Bereich(4000, 4999, SN),
            new Bereich(6000, 6999, ST),
            new Bereich(7000, 7999, TH),
            new Bereich(8000, 9999, SN),
            new Bereich(10000, 13999, BE),
            new Bereich(14000, 16999, BB),
            new Bereich(17000, 19999, MV),
            new Bereich(20000, 20999, HH),
            new Bereich(21000, 21999, NI),
            new Bereich(22000, 22999, HH),
            new Bereich(23000, 25999, SH),
            new Bereich(26000, 27999, NI),
            new Bereich(28000, 28999, HB),
            new Bereich(29000, 31999, NI),
            new Bereich(32000, 33999, NW),
            new Bereich(34000, 36999, HE),
            new Bereich(37000, 38999, NI),
            new Bereich(39000, 39999, ST),
            new Bereich(40000, 53999, NW),
            new Bereich(54000, 56999, RP),
            new Bereich(57000, 59999, NW),
            new Bereich(60000, 65999, HE),
            new Bereich(66000, 66999, SL),
            new Bereich(67000, 67999, RP),
            new Bereich(68000, 79999, BW),
            new Bereich(80000, 87999, BY),
            new Bereich(88000, 89999, BW),
            new Bereich(90000, 97999, BY),
            new Bereich(98000, 99999, TH));

    private PlzBundesland() {
    }

    /**
     * Bundesland zur Postleitzahl, leer bei ungültiger Eingabe. Leerzeichen und ein
     * vorangestelltes „D-“ werden ignoriert.
     */
    public static Optional<Bundesland> vorschlag(String plz) {
        return alsZahl(plz).flatMap(zahl -> BEREICHE.stream()
                .filter(b -> zahl >= b.von() && zahl <= b.bis())
                .map(Bereich::land)
                .findFirst());
    }

    /** Liegt die Postleitzahl in der Stadt Augsburg (Augsburger Friedensfest am 8. August)? */
    public static boolean istAugsburg(String plz) {
        return alsZahl(plz).map(zahl -> zahl >= 86150 && zahl <= 86199).orElse(false);
    }

    private static Optional<Integer> alsZahl(String plz) {
        if (plz == null) {
            return Optional.empty();
        }
        String ziffern = plz.strip();
        if (ziffern.regionMatches(true, 0, "D-", 0, 2)) {
            ziffern = ziffern.substring(2).strip();
        }
        if (ziffern.length() != 5 || !ziffern.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(ziffern));
    }
}
