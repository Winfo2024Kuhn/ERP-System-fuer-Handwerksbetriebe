package org.example.kalkulationsprogramm.dto.Telefon;

import java.time.LocalDateTime;
import java.util.List;

public record TelefonAnrufDto(Long id,
                              LocalDateTime zeitpunkt,
                              String art,
                              Integer anrufbeantworter,
                              String nummer,
                              String eigeneNummer,
                              int dauerMinuten,
                              String nameFritzbox,
                              String zuordnung,
                              KontaktKurzDto kontakt,
                              List<KontaktKurzDto> kandidaten,
                              Long sprachnachrichtId) {
}
