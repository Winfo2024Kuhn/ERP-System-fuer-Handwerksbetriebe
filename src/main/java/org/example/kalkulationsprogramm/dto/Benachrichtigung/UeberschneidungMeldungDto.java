package org.example.kalkulationsprogramm.dto.Benachrichtigung;

/**
 * Überschneidungs-Meldung für die Nachrichten der Handy-App
 * (GET /api/zeiterfassung/ueberschneidungen). Zeiten als ISO-Text ohne Zeitzone.
 *
 * @param bis {@code null}, wenn die Handy-Buchung beim Melden noch lief
 */
public record UeberschneidungMeldungDto(Long id, String von, String bis, String titel, String text,
        String erstelltAm) {
}
