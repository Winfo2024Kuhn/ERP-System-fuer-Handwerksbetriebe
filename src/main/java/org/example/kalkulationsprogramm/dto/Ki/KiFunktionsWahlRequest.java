package org.example.kalkulationsprogramm.dto.Ki;

/**
 * Anbieter und Modell für eine einzelne KI-Funktion.
 *
 * @param anbieter {@code null} oder leer = zurück auf den Standard-Anbieter
 * @param modell   {@code null} oder leer = Standard-Modell des Anbieters
 */
public record KiFunktionsWahlRequest(String anbieter, String modell) {
}
