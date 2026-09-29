package org.example.kalkulationsprogramm.dto.Telefon;

import java.util.List;

/**
 * Ereignis für das Anruf-Fenster.
 *
 * @param status KLINGELT, IM_GESPRAECH, ANRUFBEANTWORTER, BEENDET
 */
public record LiveAnrufDto(String verbindungsId,
                           String status,
                           String nummer,
                           KontaktKurzDto kontakt,
                           List<KontaktKurzDto> kandidaten,
                           boolean angenommen) {
}
