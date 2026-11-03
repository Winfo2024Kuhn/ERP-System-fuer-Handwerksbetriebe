package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.util.List;

/** Rechte eines obersten Ordners: alle Abteilungen (nach Name) mit ihrer Stufe. Nur für Admins. */
public record FirmendokumentOrdnerRechteDto(Long ordnerId, String ordnerName, boolean eingeschraenkt,
        List<FirmendokumentAbteilungsRechtDto> abteilungen) {
}
