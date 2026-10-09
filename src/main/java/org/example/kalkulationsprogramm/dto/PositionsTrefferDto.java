package org.example.kalkulationsprogramm.dto;

/**
 * Ein Lieferanten-Dokument, das über seine Positionen gefunden wurde.
 *
 * @param dokumentId     ID des Lieferanten-Dokuments
 * @param trefferText    erste passende Position, z. B. „Flachstahl 50x5 · S235JR · Charge 123456 · 12 Stück“
 * @param weitereTreffer wie viele weitere Positionen dieses Dokuments passen
 */
public record PositionsTrefferDto(Long dokumentId, String trefferText, int weitereTreffer) {
}
