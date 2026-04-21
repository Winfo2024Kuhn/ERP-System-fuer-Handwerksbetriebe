package org.example.kalkulationsprogramm.dto.Leistung;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Leichtgewichtiges DTO für die Chip-Darstellung einer WPS im Leistungseditor.
 * Enthält nur die Felder, die fürs Anzeigen eines Chips gebraucht werden –
 * vollständige WPS-Daten liefert {@code /api/wps/{id}}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WpsRefDto {
    private Long id;
    private String wpsNummer;
    private String bezeichnung;
    private String schweissProzes;
    private String grundwerkstoff;
}
