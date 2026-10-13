package org.example.kalkulationsprogramm.dto.Freigabe;

import java.time.LocalDateTime;

/**
 * Offener Online-Link, der beim Versand des aktuellen Angebots ungültig wird – ein älterer
 * Link desselben Dokuments ({@code diesesDokument = true}) oder der eines anderen Angebots im
 * Vorgang. Der Dokument-Editor fragt damit vor dem Versand nach.
 */
public record AbzuloesendeFreigabeDto(Long dokumentId, String dokumentNummer, String dokumentArt,
                                      LocalDateTime ablaufDatum, boolean diesesDokument)
{
}
