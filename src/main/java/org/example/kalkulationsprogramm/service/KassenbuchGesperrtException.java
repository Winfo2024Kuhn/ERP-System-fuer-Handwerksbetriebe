package org.example.kalkulationsprogramm.service;

import lombok.Getter;

/**
 * Der Vorgang wurde abgelehnt, weil er eine bereits festgeschriebene
 * Aufzeichnung veraendert haette.
 *
 * <p>Kein Programmfehler, sondern der Normalfall einer gefuehrten
 * Buchhaltung: was einmal festgeschrieben ist, wird nicht ueberschrieben,
 * sondern storniert und neu gebucht. Die Controller uebersetzen das in einen
 * HTTP 409 mit einem Text, der dem Nutzer sagt, was er stattdessen tun kann.</p>
 */
@Getter
public class KassenbuchGesperrtException extends RuntimeException {

    /** Was der Nutzer tun kann, um trotzdem ans Ziel zu kommen. */
    private final String loesungshinweis;

    public KassenbuchGesperrtException(String message, String loesungshinweis) {
        super(message);
        this.loesungshinweis = loesungshinweis;
    }

    public KassenbuchGesperrtException(String message) {
        this(message, null);
    }
}
