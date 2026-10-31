package org.example.kalkulationsprogramm.dto.Textbaustein;

/**
 * Ein Platzhalter, den Textbausteine enthalten dürfen.
 *
 * @param token        exakt so, wie er im Text steht, z. B. {@code {{KUNDENNAME}}}
 * @param label        kurze Bezeichnung für Knöpfe, z. B. „Kundenname“
 * @param beschreibung ein Satz, was später an dieser Stelle steht
 * @param beispiel     Beispielwert, den die Oberfläche statt einer Vorschau zeigt
 * @param gruppe       Überschrift, unter der die Oberfläche den Platzhalter einsortiert
 */
public record TextbausteinPlatzhalterDto(
        String token,
        String label,
        String beschreibung,
        String beispiel,
        String gruppe) {
}
