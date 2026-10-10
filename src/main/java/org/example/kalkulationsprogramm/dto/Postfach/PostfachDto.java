package org.example.kalkulationsprogramm.dto.Postfach;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Postfach für die Einstellungen. Das Passwort wird nie ausgeliefert – nur, ob eins
 * gesetzt ist.
 */
public record PostfachDto(
        Long id,
        String emailAdresse,
        String anzeigename,
        boolean aktiv,
        int sortierung,
        boolean hauptpostfach,
        boolean fuerGeschaeftsdokumente,
        String benutzername,
        boolean passwortGesetzt,
        String smtpHost,
        Integer smtpPort,
        String imapHost,
        Integer imapPort,
        boolean abrufAktiv,
        LocalDateTime letzterAbrufAm,
        String letzterAbrufFehler,
        List<BenutzerRefDto> zugewieseneBenutzer) {

    public record BenutzerRefDto(Long id, String displayName) {
    }
}
