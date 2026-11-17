package org.example.kalkulationsprogramm.dto.AusgangsDokumentStand;

/**
 * Antwort auf „Stand festhalten“ ohne PDF.
 *
 * @param festgehalten {@code false}, wenn der Inhalt dem neuesten Stand gleicht (dann ist {@code stand} {@code null})
 */
public record StandFesthaltenDto(boolean festgehalten, StandEintragDto stand) {
}
