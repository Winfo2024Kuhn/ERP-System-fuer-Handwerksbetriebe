package org.example.kalkulationsprogramm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Einzige Quelle der Wahrheit für den Dokument-Pauschalrabatt.
 *
 * <p>Der Rabatt wurde an drei Stellen unabhängig voneinander gerechnet — im PDF, im
 * ZUGFeRD-Betrag und im Fallback aus {@code positionenJson}. Zwei davon benutzten die
 * Faktor-Variante {@code round2(netto × round4(1 − r/100))}, eine die Abzugs-Variante
 * {@code netto − round2(netto × r/100)}. Beide sind für sich plausibel, liefern aber
 * unterschiedliche Cent-Beträge:</p>
 *
 * <pre>
 *   netto = 100,10 €, Rabatt 5 %
 *   Faktor-Variante : round2(100,10 × 0,9500) = 95,10
 *   Abzugs-Variante : 100,10 − round2(5,005) = 100,10 − 5,01 = 95,09
 * </pre>
 *
 * <p>Das ist kein Schönheitsfehler: Weicht der gespeicherte Betrag um einen Cent vom
 * versendeten PDF ab, hält der Korrekturlauf ein bereits korrektes Dokument für falsch,
 * schreibt es um und hinterlässt auf einer festgeschriebenen Rechnung einen
 * GEAENDERT-Audit-Eintrag. Im PDF taucht dann wieder ein „Verbleibender Restbetrag
 * 0,01 €" auf.</p>
 *
 * <p>Verbindlich ist die <strong>Abzugs-Variante</strong>, weil sie den Rabattbetrag so
 * rundet, wie er im PDF als eigene Zeile ausgewiesen wird — die angezeigten Zahlen
 * müssen sich aufaddieren lassen. Das Frontend rechnet in
 * {@code helpers.ts#nettoNachGlobalRabatt} identisch.</p>
 */
public final class RabattRechner {

    private static final BigDecimal HUNDERT = new BigDecimal("100");

    private RabattRechner() {
    }

    /**
     * Begrenzt einen Rabatt-Prozentwert auf den gültigen Bereich 0–100.
     *
     * @return {@code null}, wenn kein wirksamer Rabatt vorliegt (null, ≤ 0)
     */
    public static BigDecimal normalisiereProzent(BigDecimal rabattProzent) {
        if (rabattProzent == null || rabattProzent.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return rabattProzent.min(HUNDERT);
    }

    /**
     * Rabattbetrag auf zwei Nachkommastellen — genau der Wert, der im PDF als
     * Rabattzeile steht.
     */
    public static BigDecimal rabattBetrag(BigDecimal netto, BigDecimal rabattProzent) {
        BigDecimal prozent = normalisiereProzent(rabattProzent);
        if (netto == null || prozent == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // Eingangs-Netto selbst normalisieren: sonst haengt das Ergebnis davon ab, ob der
        // Aufrufer schon gerundet hat. Genau daran liefen PDF-Anzeige (ungerundete
        // Positionssumme) und gespeicherter Betrag (gerundet) auseinander.
        return normalisiereBetrag(netto).multiply(prozent).divide(HUNDERT, 2, RoundingMode.HALF_UP);
    }

    /** Kaufmaennisch auf Cent — der Vertrag fuer jeden Geldbetrag in dieser Klasse. */
    public static BigDecimal normalisiereBetrag(BigDecimal betrag) {
        return betrag == null ? null : betrag.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Nettobetrag nach Abzug des Pauschalrabatts.
     *
     * @param netto         Nettosumme vor Pauschalrabatt (Positions-Rabatte sind bereits drin)
     * @param rabattProzent Pauschalrabatt in Prozent; {@code null}/≤ 0 lässt den Betrag unverändert
     */
    public static BigDecimal nettoNachRabatt(BigDecimal netto, BigDecimal rabattProzent) {
        if (netto == null) {
            return null;
        }
        return normalisiereBetrag(netto).subtract(rabattBetrag(netto, rabattProzent));
    }
}
