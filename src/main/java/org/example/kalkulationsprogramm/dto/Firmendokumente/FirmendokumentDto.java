package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FirmendokumentDto(Long id, Long ordnerId, String name, String originalDateiname, long groesseBytes,
        LocalDateTime hochgeladenAm, String hochgeladenVon, LocalDate faelligAm, String url) {
}
