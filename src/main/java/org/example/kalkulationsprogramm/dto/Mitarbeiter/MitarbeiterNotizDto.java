package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import java.time.LocalDateTime;

public record MitarbeiterNotizDto(
        Long id,
        String inhalt,
        LocalDateTime erstelltAm,
        Long mitarbeiterId) {
}
