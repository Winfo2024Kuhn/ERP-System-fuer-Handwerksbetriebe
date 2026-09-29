package org.example.kalkulationsprogramm.dto.Telefon;

/**
 * Anruf oder Nachricht von Hand zuordnen: genau eins von kundeId/lieferantId/steuerberaterId.
 *
 * @param ansprechpartnerId nur zusammen mit steuerberaterId und nummerMerken: die Nummer wird
 *                          beim Ansprechpartner der Kanzlei als Telefon eingetragen
 */
public record TelefonZuordnenDto(Long kundeId, Long lieferantId, Long steuerberaterId, boolean nummerMerken,
                                 Long ansprechpartnerId) {

    public TelefonZuordnenDto(Long kundeId, Long lieferantId, Long steuerberaterId, boolean nummerMerken) {
        this(kundeId, lieferantId, steuerberaterId, nummerMerken, null);
    }
}
