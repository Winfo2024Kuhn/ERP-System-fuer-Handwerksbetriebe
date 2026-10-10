package org.example.kalkulationsprogramm.dto.Postfach;

/**
 * Anlegen/Bearbeiten eines Postfachs. {@code passwort} leer oder {@code null} = unverändert.
 */
public record PostfachSpeichernRequest(
        String emailAdresse,
        String anzeigename,
        Boolean aktiv,
        Integer sortierung,
        Boolean hauptpostfach,
        Boolean fuerGeschaeftsdokumente,
        String benutzername,
        String passwort,
        String smtpHost,
        Integer smtpPort,
        String imapHost,
        Integer imapPort) {
}
