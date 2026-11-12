package org.example.kalkulationsprogramm.service.vorschau;

/**
 * Alle Umrechnungsplätze sind belegt ({@link Umrechnungsplaetze}). Ein vorübergehender Zustand, anders als ein
 * unlesbares Format: Die Antwort (HTTP 503 mit {@code Retry-After}) darf nirgends gespeichert werden, sonst bliebe
 * ein Gerät dauerhaft auf der Ausweich-Antwort sitzen.
 */
public class UmrechnungAusgelastetException extends RuntimeException {

    public UmrechnungAusgelastetException() {
        super("Bildumrechnung ausgelastet");
    }
}
