package org.example.kalkulationsprogramm.dto.Freigabe;

import java.time.LocalDateTime;

/**
 * Offener Online-Link eines anderen Angebots, der beim Versand des aktuellen Angebots
 * ungültig wird. Der Dokument-Editor fragt damit vor dem Versand nach.
 */
public record AbzuloesendeFreigabeDto(Long dokumentId, String dokumentNummer, String dokumentArt,
                                      LocalDateTime ablaufDatum)
{
}
