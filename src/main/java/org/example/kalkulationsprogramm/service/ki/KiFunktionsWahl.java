package org.example.kalkulationsprogramm.service.ki;

/**
 * Eigene Wahl des Betriebs für eine KI-Funktion: dieser Anbieter, und
 * optional dieses Modell. Ohne eigene Wahl folgt die Funktion dem Standard-Anbieter.
 *
 * @param modell {@code null} = das Modell der üblichen Stufe dieses Anbieters
 */
public record KiFunktionsWahl(KiAnbieterTyp anbieter, String modell) {
}
