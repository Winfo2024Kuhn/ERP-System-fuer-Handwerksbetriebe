package org.example.kalkulationsprogramm.util;

import static java.util.Map.entry;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Übersetzt die frei eingetippte Einheit einer Position („Stk“, „m²“, „Std.“) in den Code
 * nach UN/ECE-Empfehlung 20, den die E-Rechnung je Position verlangt (EN 16931, BT-130).
 *
 * <p>Die Einheit ist im Editor ein Freitextfeld. Was hier nicht steht, wird zu {@link #EINHEIT}
 * („Einheit“): Der Code ist immer gültig und behauptet nichts – ein geratener Code machte aus
 * „Pal.“ schnell eine falsche Mengenangabe.</p>
 */
public final class EinheitenCode {

    /** UN/ECE-Code „one“ – gilt, wenn die Einheit fehlt oder unbekannt ist. */
    public static final String EINHEIT = "C62";

    private static final Map<String, String> CODES = Map.ofEntries(
            entry("stk", "H87"), entry("st", "H87"), entry("stck", "H87"),
            entry("stück", "H87"), entry("stueck", "H87"),
            entry("m", "MTR"), entry("lfm", "MTR"), entry("lfdm", "MTR"), entry("meter", "MTR"),
            entry("m²", "MTK"), entry("m2", "MTK"), entry("qm", "MTK"),
            entry("m³", "MTQ"), entry("m3", "MTQ"), entry("cbm", "MTQ"),
            entry("h", "HUR"), entry("std", "HUR"), entry("stunde", "HUR"), entry("stunden", "HUR"),
            entry("min", "MIN"),
            entry("tag", "DAY"), entry("tage", "DAY"),
            entry("kg", "KGM"), entry("g", "GRM"), entry("t", "TNE"),
            entry("l", "LTR"), entry("ltr", "LTR"), entry("liter", "LTR"),
            entry("km", "KMT"), entry("cm", "CMT"), entry("mm", "MMT"),
            entry("psch", "LS"), entry("pschl", "LS"), entry("pausch", "LS"), entry("pauschal", "LS"),
            entry("satz", "SET"), entry("set", "SET"),
            entry("paar", "PR"));

    /** Wer den Code selbst eintippt („H87“), bekommt ihn unverändert. */
    private static final Set<String> BEKANNTE_CODES = Set.copyOf(CODES.values());

    private EinheitenCode() {
    }

    /**
     * @param einheit Einheit aus dem Editor, darf {@code null} sein
     * @return UN/ECE-Code, nie {@code null}
     */
    public static String unEceCode(String einheit) {
        if (einheit == null) {
            return EINHEIT;
        }
        String getrimmt = einheit.trim();
        String alsCode = getrimmt.toUpperCase(Locale.ROOT);
        if (BEKANNTE_CODES.contains(alsCode)) {
            return alsCode;
        }
        // „Std.“, „lfd. m“: Punkte und Leerzeichen gehören nicht zur Einheit
        String schluessel = getrimmt.toLowerCase(Locale.ROOT).replace(".", "").replace(" ", "");
        return CODES.getOrDefault(schluessel, EINHEIT);
    }
}
