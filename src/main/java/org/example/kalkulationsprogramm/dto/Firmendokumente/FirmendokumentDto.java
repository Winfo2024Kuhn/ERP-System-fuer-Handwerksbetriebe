package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein Firmendokument für die Oberfläche. {@code dateiart}: {@code PDF}, {@code BILD} oder {@code SONSTIGE} (aus der
 * Endung des Originalnamens); {@code darfAendern}: der Angemeldete darf das Dokument bearbeiten und löschen.
 */
public record FirmendokumentDto(Long id, Long ordnerId, String name, String originalDateiname, long groesseBytes,
        LocalDateTime hochgeladenAm, String hochgeladenVon, LocalDate faelligAm, String url,
        String dateiart, boolean darfAendern) {
}
