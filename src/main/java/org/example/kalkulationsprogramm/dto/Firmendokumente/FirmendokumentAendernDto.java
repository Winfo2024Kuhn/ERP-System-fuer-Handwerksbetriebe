package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.time.LocalDate;

/** Umbenennen, Verschieben und Fälligkeitsdatum in einem; {@code faelligAm == null} löscht das Datum. */
public record FirmendokumentAendernDto(String name, Long ordnerId, LocalDate faelligAm) {
}
