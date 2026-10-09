package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

/**
 * Eine Position, wie sie aus dem Dokument gelesen wurde (KI-Antwort oder
 * ZUGFeRD/XRechnung) – noch nicht gespeichert.
 *
 * @param einzelpreis      Preis je {@code preiseinheit}, nicht die Positionssumme
 * @param gesamtpreisNetto Positionssumme netto; bei Rabatten negativ
 * @param werkstoff        Werkstoff/Güte, z. B. „S235JR+AR“ oder „1.4301“ – falls aufgedruckt
 * @param charge           Charge bzw. Schmelze – falls aufgedruckt
 * @param abmessung        Abmessung, z. B. „50x5“ – falls getrennt aufgedruckt
 */
public record AusgelesenePosition(
        PositionsArt positionsArt,
        String externeArtikelnummer,
        String bezeichnung,
        BigDecimal menge,
        String mengeneinheit,
        BigDecimal einzelpreis,
        String preiseinheit,
        BigDecimal gesamtpreisNetto,
        String werkstoff,
        String charge,
        String abmessung) {

    /** Position ohne Werkstoff, Charge und Abmessung (Rechnungen, ZUGFeRD). */
    public AusgelesenePosition(PositionsArt positionsArt, String externeArtikelnummer, String bezeichnung,
            BigDecimal menge, String mengeneinheit, BigDecimal einzelpreis, String preiseinheit,
            BigDecimal gesamtpreisNetto) {
        this(positionsArt, externeArtikelnummer, bezeichnung, menge, mengeneinheit, einzelpreis, preiseinheit,
                gesamtpreisNetto, null, null, null);
    }
}
