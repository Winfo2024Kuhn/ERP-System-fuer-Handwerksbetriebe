package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

/**
 * Eine Position, wie sie aus dem Dokument gelesen wurde (KI-Antwort oder
 * ZUGFeRD/XRechnung) – noch nicht gespeichert.
 *
 * @param einzelpreis      Preis je {@code preiseinheit}, nicht die Positionssumme
 * @param gesamtpreisNetto Positionssumme netto; bei Rabatten negativ
 */
public record AusgelesenePosition(
        PositionsArt positionsArt,
        String externeArtikelnummer,
        String bezeichnung,
        BigDecimal menge,
        String mengeneinheit,
        BigDecimal einzelpreis,
        String preiseinheit,
        BigDecimal gesamtpreisNetto) {
}
