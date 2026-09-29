package org.example.kalkulationsprogramm.dto.Telefon;

import java.time.LocalDateTime;
import java.util.List;

public record SprachnachrichtDto(Long id,
                                 int anrufbeantworter,
                                 LocalDateTime zeitpunkt,
                                 String nummer,
                                 int dauerSekunden,
                                 boolean neu,
                                 LocalDateTime abgehoertAm,
                                 String abgehoertVon,
                                 String zuordnung,
                                 KontaktKurzDto kontakt,
                                 List<KontaktKurzDto> kandidaten,
                                 String nameFritzbox) {
}
