package org.example.kalkulationsprogramm.dto.Telefon;

/** Anruf oder Nachricht von Hand zuordnen: genau eins von kundeId/lieferantId. */
public record TelefonZuordnenDto(Long kundeId, Long lieferantId, boolean nummerMerken) {
}
