package org.example.kalkulationsprogramm.dto.Benachrichtigung;

/** Eine Kategorie der Glocke; {@code count} zählt nur Einträge, die der Benutzer noch nicht erledigt hat. */
public record BenachrichtigungKategorieDto(
        String type,
        String label,
        int count,
        String icon,
        String link) {
}
