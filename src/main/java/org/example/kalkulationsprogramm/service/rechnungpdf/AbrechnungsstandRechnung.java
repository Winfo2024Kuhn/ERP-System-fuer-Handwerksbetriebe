package org.example.kalkulationsprogramm.service.rechnungpdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rechnet die Betraege des Abschnitts „Abrechnungsstand“ auf Rechnungen mit Bezug zu Angebot
 * oder Auftragsbestaetigung. Reine Klasse ohne Spring und ohne PDF.
 *
 * <p>Die Differenz zur Auftragssumme ist eine Ausgleichsgroesse, kein Positionsvergleich: Sie
 * entsteht nur, wenn die Rechnung den Auftrag abschliesst, und geht immer in den Rest ein. Ob
 * die Differenzzeile im PDF gedruckt wird ({@link AbschlussZeileAnpassung#ausblenden()}),
 * aendert an diesen Betraegen nichts.
 *
 * @param auftragNetto       Auftragssumme netto (Angebot bzw. Auftragsbestaetigung)
 * @param vorrechnungenNetto Summe der bereits gestellten Rechnungen netto
 * @param dieseNetto         Nettobetrag dieser Rechnung (nach Rabatt, bei der Schlussrechnung
 *                           schon ohne die Vorrechnungen)
 * @param schliesstAuftragAb Rechnung schliesst den Auftrag ab (Schlussrechnung bzw. einfache
 *                           Rechnung ueber den ganzen Auftrag)
 */
public record AbrechnungsstandRechnung(
        BigDecimal auftragNetto,
        BigDecimal vorrechnungenNetto,
        BigDecimal dieseNetto,
        boolean schliesstAuftragAb) {

    /** Umsatzsteuersatz des Rechnungs-PDFs. */
    public static final BigDecimal MWST_SATZ = new BigDecimal("0.19");

    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final BigDecimal HUNDERT = new BigDecimal("100");

    /**
     * @param nettoNachRabatt  Summe der Positionen dieser Rechnung nach Rabatt (bzw. Abschlagsbetrag)
     * @param schlussrechnung  Schlussrechnung: rechnet ihre Positionen abzueglich der Vorrechnungen ab
     */
    public static AbrechnungsstandRechnung fuer(BigDecimal auftragNetto, BigDecimal vorrechnungenNetto,
                                                BigDecimal nettoNachRabatt, boolean schlussrechnung,
                                                boolean schliesstAuftragAb) {
        BigDecimal diese = schlussrechnung ? nettoNachRabatt.subtract(vorrechnungenNetto) : nettoNachRabatt;
        return new AbrechnungsstandRechnung(auftragNetto, vorrechnungenNetto, diese, schliesstAuftragAb);
    }

    /** Umsatzsteuer zu einem Nettobetrag, kaufmaennisch auf Cent gerundet. */
    public static BigDecimal steuer(BigDecimal netto) {
        return netto.multiply(MWST_SATZ).setScale(2, RoundingMode.HALF_UP);
    }

    /** Bruttobetrag zu einem Nettobetrag, Steuer kaufmaennisch auf Cent gerundet. */
    public static BigDecimal brutto(BigDecimal netto) {
        if (netto == null) {
            return BigDecimal.ZERO;
        }
        return netto.add(steuer(netto));
    }

    public BigDecimal dieseSteuer() {
        return steuer(dieseNetto);
    }

    public BigDecimal dieseBrutto() {
        return dieseNetto.add(dieseSteuer());
    }

    /**
     * Unterschied zwischen Auftrag und Abgerechnetem, nur bei abschliessender Rechnung:
     * {@code > 0} Leistungen sind entfallen, {@code < 0} Zusatzleistungen.
     */
    public BigDecimal differenzNetto() {
        if (!schliesstAuftragAb) {
            return BigDecimal.ZERO;
        }
        return auftragNetto.subtract(vorrechnungenNetto).subtract(dieseNetto);
    }

    /** Eine Differenzzeile gibt es erst ab einem Cent Unterschied. */
    public boolean hatDifferenz() {
        return differenzNetto().abs().compareTo(CENT) >= 0;
    }

    /** {@code true}: weniger abgerechnet als beauftragt („Nicht angefallen“). */
    public boolean differenzEntfallen() {
        return differenzNetto().compareTo(BigDecimal.ZERO) > 0;
    }

    /** Noch offener Rest netto; die Differenz ist immer abgezogen, auch wenn ihre Zeile fehlt. */
    public BigDecimal restNetto() {
        return auftragNetto.subtract(vorrechnungenNetto).subtract(dieseNetto).subtract(differenzNetto());
    }

    /** Abgerechneter Anteil in Prozent (eine Nachkommastelle); abschliessend immer 100. */
    public double prozentAbgerechnet() {
        if (schliesstAuftragAb) {
            return 100d;
        }
        return vorrechnungenNetto.add(dieseNetto).multiply(HUNDERT)
                .divide(auftragNetto, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Anteil dieser Rechnung an der Auftragssumme in Prozent (eine Nachkommastelle). */
    public BigDecimal anteilDieser() {
        return dieseNetto.multiply(HUNDERT).divide(auftragNetto, 1, RoundingMode.HALF_UP);
    }
}
