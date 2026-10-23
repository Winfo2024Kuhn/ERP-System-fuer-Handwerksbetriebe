package org.example.kalkulationsprogramm.dto.Produktkategroie;

/**
 * Eine Zeile der Ordner-Übersicht: Kennzahlen eines Ordners inkl. aller Unterordner,
 * berechnet aus abgeschlossenen Projekten.
 *
 * @param stundenProEinheit {@code null}, wenn keine Menge erfasst ist
 */
public record KategorieAuswertungZeileDto(
        Long kategorieId,
        String bezeichnung,
        String bildUrl,
        String verrechnungseinheit,
        boolean leaf,
        long projektAnzahl,
        double stunden,
        double menge,
        Double stundenProEinheit,
        long leistungAnzahl) {
}
