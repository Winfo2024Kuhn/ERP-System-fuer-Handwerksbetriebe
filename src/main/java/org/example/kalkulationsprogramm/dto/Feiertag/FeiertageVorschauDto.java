package org.example.kalkulationsprogramm.dto.Feiertag;

import java.time.LocalDate;
import java.util.List;

/**
 * Vorschau in den Firmendaten: welches Bundesland die Postleitzahl nahelegt und welche
 * Feiertage dort gelten (Issue #222).
 *
 * @param vorgeschlagenesBundesland Kürzel aus der Postleitzahl, null wenn keine gültige PLZ
 * @param bundesland                Kürzel, für das die Feiertage unten gelten
 */
public record FeiertageVorschauDto(String vorgeschlagenesBundesland, String bundesland, String bundeslandName,
        int jahr, List<Eintrag> feiertage, List<String> hinweise) {

    public record Eintrag(LocalDate datum, String bezeichnung) {
    }
}
