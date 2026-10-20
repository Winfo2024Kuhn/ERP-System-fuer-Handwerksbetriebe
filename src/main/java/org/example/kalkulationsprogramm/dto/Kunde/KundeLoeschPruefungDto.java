package org.example.kalkulationsprogramm.dto.Kunde;

import java.time.LocalDate;
import java.util.List;

/**
 * Darf der Kunde komplett gelöscht werden (Issue #264)?
 *
 * @param loeschbar      {@code true}, wenn keine aufbewahrungspflichtigen Unterlagen am Kunden hängen
 * @param hindernisse    fertige Klartexte wie „2 Projekte“, „3 Angebote/Rechnungen“
 * @param aufbewahrenBis bis wann bei einer Datenschutz-Sperre aufbewahrt würde; {@code null}, wenn löschbar
 */
public record KundeLoeschPruefungDto(boolean loeschbar, List<String> hindernisse, LocalDate aufbewahrenBis) {
}
