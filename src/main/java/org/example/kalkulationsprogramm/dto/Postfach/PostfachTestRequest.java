package org.example.kalkulationsprogramm.dto.Postfach;

/**
 * Verbindungstest. {@code passwort} leer = gespeichertes Passwort des Postfachs {@code id}.
 */
public record PostfachTestRequest(
        Long id,
        String benutzername,
        String passwort,
        String smtpHost,
        Integer smtpPort,
        String imapHost,
        Integer imapPort,
        String testEmpfaenger) {
}
