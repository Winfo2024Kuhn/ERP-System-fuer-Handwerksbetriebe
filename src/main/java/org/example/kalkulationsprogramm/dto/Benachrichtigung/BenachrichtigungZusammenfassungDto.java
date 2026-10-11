package org.example.kalkulationsprogramm.dto.Benachrichtigung;

import java.util.List;

/** Inhalt der Benachrichtigungs-Glocke: Gesamtzahl, Kategorien mit Zähler und die jüngsten Einträge. */
public record BenachrichtigungZusammenfassungDto(
        int totalCount,
        List<BenachrichtigungKategorieDto> categories,
        List<BenachrichtigungEintragDto> recentItems) {
}
