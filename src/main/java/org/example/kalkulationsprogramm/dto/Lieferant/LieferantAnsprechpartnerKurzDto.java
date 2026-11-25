package org.example.kalkulationsprogramm.dto.Lieferant;

/**
 * Ansprechpartner in der Lieferantenliste der Handy-App ({@code GET /api/zeiterfassung/lieferanten},
 * Schlüssel {@code ansprechpartner}).
 *
 * @param name „Vorname Nachname“, ohne Vorname nur der Nachname
 */
public record LieferantAnsprechpartnerKurzDto(Long id, String name, String aufgabe, String telefon,
                                              String mobiltelefon, String email) {
}
