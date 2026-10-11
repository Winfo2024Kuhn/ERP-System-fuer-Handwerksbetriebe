package org.example.kalkulationsprogramm.dto.Benachrichtigung;

/**
 * Ein Eintrag der Glocke. {@code id} ist eindeutig und stabil (Art und Kennung, z. B. {@code TERMIN:123})
 * und wird zum Erledigen zurückgeschickt.
 */
public record BenachrichtigungEintragDto(
        String id,
        String type,
        String title,
        String subtitle,
        String timestamp,
        String link) {
}
