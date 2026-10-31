package org.example.kalkulationsprogramm.service.ki;

import tools.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Ein Werkzeug, das die KI selbst aufrufen darf (Function Calling), z. B. „im
 * Programm suchen“. Der Dienst führt die Aufrufe aus und schickt die Ergebnisse
 * in der nächsten Runde zurück.
 *
 * @param name         eindeutiger Name, Buchstaben, Ziffern und Unterstrich
 * @param beschreibung wofür das Werkzeug da ist – die KI entscheidet danach
 * @param parameter    Form der Argumente, im Gemini-Stil wie bei {@link KiAnfrage#schema()}
 *                     ({@code "type": "OBJECT"}); andere Anbieter übersetzen sie
 */
public record KiWerkzeug(String name, String beschreibung, JsonNode parameter) {

    public KiWerkzeug {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parameter, "parameter");
        beschreibung = beschreibung == null ? "" : beschreibung;
    }
}
