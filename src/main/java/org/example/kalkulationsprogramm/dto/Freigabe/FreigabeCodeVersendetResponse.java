package org.example.kalkulationsprogramm.dto.Freigabe;

import java.time.LocalDateTime;

/**
 * Antwort auf POST /api/internal/freigabe/{uuid}/code.
 *
 * @param emailMaskiert             Empfänger-Adresse(n), z. B. "m•••@example.de" – damit der
 *                                  Kunde weiß, wo er nachsehen muss, ohne dass der Link die
 *                                  volle Adresse verrät
 * @param gueltigBis                Ablauf des verschickten Codes
 * @param erneutAnfordernInSekunden Wartezeit bis "Neuen Code anfordern" wieder geht
 */
public record FreigabeCodeVersendetResponse(
        boolean success,
        String emailMaskiert,
        LocalDateTime gueltigBis,
        long erneutAnfordernInSekunden)
{
}
