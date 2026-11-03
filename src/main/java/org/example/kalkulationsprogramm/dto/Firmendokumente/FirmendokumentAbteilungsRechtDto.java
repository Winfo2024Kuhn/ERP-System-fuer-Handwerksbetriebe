package org.example.kalkulationsprogramm.dto.Firmendokumente;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;

/** Eine Abteilung mit ihrer Stufe an einem obersten Ordner; {@code KEINE} = sieht den Zweig nicht. */
public record FirmendokumentAbteilungsRechtDto(Long abteilungId, String name, FirmendokumentZugriffsStufe stufe) {
}
