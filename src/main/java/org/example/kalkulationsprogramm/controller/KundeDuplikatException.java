package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;

/**
 * Wird vom {@link KundeController} geworfen, wenn beim Anlegen eines Kunden
 * ein potenzielles Duplikat erkannt wurde und der User die Anlage noch nicht
 * ausdrücklich bestätigt hat (Header {@code X-Duplikat-Bestaetigt: true}).
 *
 * <p>Wird in {@link KundeController#handleDuplikat} in einen HTTP 409 mit dem
 * Treffer-DTO als Body übersetzt. Das Frontend zeigt daraufhin den
 * Bestätigungs-Modal mit den möglichen Duplikaten.
 */
public class KundeDuplikatException extends RuntimeException {

    private final KundeDuplikatResponseDto antwort;

    public KundeDuplikatException(KundeDuplikatResponseDto antwort) {
        super("Möglicher Kunden-Duplikat erkannt.");
        this.antwort = antwort;
    }

    public KundeDuplikatResponseDto getAntwort() {
        return antwort;
    }
}
