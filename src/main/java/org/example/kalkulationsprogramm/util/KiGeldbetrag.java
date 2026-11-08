package org.example.kalkulationsprogramm.util;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Liest Geldbeträge tolerant aus einer KI-Antwort (JSON). Die KI liefert mal Zahlen,
 * mal Text wie „1.234,56“, „1,234.56“ oder „1234.56 EUR“.
 *
 * <p>Genutzt von der Steuerberater-Ablage für Lohnabrechnungen (Brutto/Netto) und
 * BWAs (Gemeinkosten).</p>
 */
@Slf4j
public final class KiGeldbetrag {

    /** "1.234" / "12.500" / "1.234.567" -- Punkte als Tausendertrenner. */
    private static final Pattern TAUSENDER_PUNKT = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})+$");

    /** "12,500" / "1,234,567" -- Kommas als Tausendertrenner (spiegelbildlich zu TAUSENDER_PUNKT). */
    private static final Pattern TAUSENDER_KOMMA = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");

    private KiGeldbetrag() {
    }

    /**
     * Liest einen Geldbetrag aus einem JSON-Feld. Toleriert Zahlen und Strings
     * ("1.234,56", "1234.56 EUR"), weil die KI beides liefern kann.
     * {@code hasNonNull} ist Pflicht: bei explizitem JSON-null wuerde
     * {@code asDouble()} stillschweigend 0 liefern.
     *
     * @return der Betrag als Absolutwert oder null, wenn nichts Verwertbares drinsteht
     */
    public static BigDecimal lese(JsonNode root, String feld) {
        if (root == null || !root.hasNonNull(feld)) {
            return null;
        }
        JsonNode node = root.get(feld);
        if (node.isNumber()) {
            // valueOf statt new BigDecimal(double): sonst 1234.5599999999999
            return BigDecimal.valueOf(node.asDouble(0.0)).abs();
        }
        if (!node.isString()) {
            return null;
        }
        String roh = node.asString("").replaceAll("[^0-9,.-]", "").trim();
        if (roh.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(normalisiere(roh)).abs();
        } catch (NumberFormatException e) {
            log.warn("[KI-Betrag] Betrag im Feld '{}' nicht lesbar", feld);
            return null;
        }
    }

    private static String normalisiere(String roh) {
        int letztesKomma = roh.lastIndexOf(',');
        int letzterPunkt = roh.lastIndexOf('.');
        if (letztesKomma >= 0 && letzterPunkt >= 0) {
            // Beide Zeichen vorhanden: das zuletzt stehende ist das Dezimalzeichen.
            // "1.234,56" (deutsch) und "1,234.56" (englisch) werden so beide richtig
            // gelesen -- vorher ergab "1,234.56" 1,23 EUR, Faktor 1000 daneben.
            return letztesKomma > letzterPunkt
                    ? roh.replace(".", "").replace(',', '.')
                    : roh.replace(",", "");
        }
        if (letzterPunkt >= 0) {
            // Nur Punkte: "12.500" und "1.234.567" sind Tausendertrenner,
            // "12500.50" und "1.5" sind Dezimalzahlen. Ohne diese Unterscheidung
            // wuerde aus 12.500 EUR still ein Betrag von 12,50 EUR.
            return TAUSENDER_PUNKT.matcher(roh).matches() ? roh.replace(".", "") : roh;
        }
        if (letztesKomma >= 0) {
            // Nur Kommas -- spiegelbildlich zur Punkt-Seite: exakt dreistellige
            // Gruppen ("12,500", "1,234,567") sind Tausendertrenner, alles
            // andere ist das deutsche Dezimalkomma ("1234,56", "0,50").
            // Der Prompt verlangt den Punkt als Dezimaltrenner; liefert die KI
            // trotzdem ein Komma mit genau drei Folgeziffern, ist das die
            // englische Tausenderschreibweise -- ein Geldbetrag mit drei
            // Nachkommastellen kommt in einer BWA nicht vor.
            return TAUSENDER_KOMMA.matcher(roh).matches() ? roh.replace(",", "") : roh.replace(',', '.');
        }
        return roh;
    }
}
