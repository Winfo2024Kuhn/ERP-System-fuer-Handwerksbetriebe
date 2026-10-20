package org.example.kalkulationsprogramm.dto.Kunde;

import java.time.LocalDate;
import java.util.List;

/**
 * Darf der Kunde komplett gelöscht werden (Issue #264)?
 *
 * @param loeschbar      {@code true}, wenn keine aufbewahrungspflichtigen Unterlagen am Kunden hängen
 * @param hindernisse    fertige Klartexte wie „2 Projekte“, „3 Angebote/Rechnungen“
 * @param aufbewahrenBis bis wann aufbewahrt werden muss, frisch aus allen aktuellen Unterlagen berechnet;
 *                       {@code null}, wenn keine Unterlagen dranhängen
 * @param anonymisierbar {@code true}, wenn der Kunde gesperrt, noch nicht anonymisiert und die Frist vorbei ist
 */
public record KundeLoeschPruefungDto(boolean loeschbar, List<String> hindernisse, LocalDate aufbewahrenBis,
        boolean anonymisierbar) {
}
