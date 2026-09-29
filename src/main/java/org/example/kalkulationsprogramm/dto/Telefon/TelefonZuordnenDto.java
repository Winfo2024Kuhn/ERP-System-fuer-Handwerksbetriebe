package org.example.kalkulationsprogramm.dto.Telefon;

/** Anruf oder Nachricht von Hand zuordnen: genau eins von kundeId/lieferantId/steuerberaterId. */
public record TelefonZuordnenDto(Long kundeId, Long lieferantId, Long steuerberaterId, boolean nummerMerken) {
}
