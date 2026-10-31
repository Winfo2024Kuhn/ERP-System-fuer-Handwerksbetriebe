package org.example.kalkulationsprogramm.service.ids;

import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

/**
 * Übersetzt Mengeneinheiten aus dem Shop (IDS-Codeliste / UN/ECE Rec. 20, z. B. {@code PCE}, {@code MTR})
 * in die Kurzformen, die Handwerker auf einer Rechnung erwarten.
 */
public final class IdsEinheiten {

    private static final Map<String, String> KURZFORM = Map.ofEntries(
            Map.entry("PCE", "Stk"), Map.entry("C62", "Stk"), Map.entry("STK", "Stk"), Map.entry("ST", "Stk"),
            Map.entry("H87", "Stk"), Map.entry("EA", "Stk"),
            Map.entry("MTR", "m"), Map.entry("M", "m"), Map.entry("LM", "m"),
            Map.entry("CMT", "cm"), Map.entry("MMT", "mm"),
            Map.entry("MTK", "m²"), Map.entry("QM", "m²"), Map.entry("M2", "m²"),
            Map.entry("MTQ", "m³"), Map.entry("M3", "m³"),
            Map.entry("KGM", "kg"), Map.entry("KG", "kg"), Map.entry("GRM", "g"), Map.entry("TNE", "t"),
            Map.entry("LTR", "l"), Map.entry("L", "l"),
            Map.entry("PK", "Pck"), Map.entry("PAK", "Pck"), Map.entry("PA", "Pck"),
            Map.entry("SET", "Satz"), Map.entry("PR", "Paar"), Map.entry("PAA", "Paar"),
            Map.entry("RO", "Rolle"), Map.entry("ROL", "Rolle"),
            Map.entry("CT", "Karton"), Map.entry("KAR", "Karton"),
            Map.entry("BX", "Box"), Map.entry("DOS", "Dose"), Map.entry("CA", "Dose"),
            Map.entry("TU", "Tube"), Map.entry("KRT", "Kartusche"),
            Map.entry("HUR", "Std"));

    private IdsEinheiten() {
    }

    /** Deutsche Kurzform; unbekannte Codes bleiben wie geliefert, leere werden zu „Stk“. */
    public static String kurzform(String code) {
        if (code == null || code.isBlank()) {
            return "Stk";
        }
        String bereinigt = code.trim();
        return KURZFORM.getOrDefault(bereinigt.toUpperCase(Locale.ROOT), bereinigt);
    }

    /** Passende Verrechnungseinheit für einen neuen Artikel; {@code null}, wenn keine passt. */
    public static Verrechnungseinheit verrechnungseinheit(String kurzform) {
        if (kurzform == null) {
            return null;
        }
        return switch (kurzform) {
            case "Stk" -> Verrechnungseinheit.STUECK;
            case "m" -> Verrechnungseinheit.LAUFENDE_METER;
            case "m²" -> Verrechnungseinheit.QUADRATMETER;
            case "kg" -> Verrechnungseinheit.KILOGRAMM;
            default -> null;
        };
    }
}
