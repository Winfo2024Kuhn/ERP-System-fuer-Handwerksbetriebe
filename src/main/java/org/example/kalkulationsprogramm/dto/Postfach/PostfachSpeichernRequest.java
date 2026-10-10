package org.example.kalkulationsprogramm.dto.Postfach;

import java.util.List;

/**
 * Anlegen/Bearbeiten eines Postfachs. {@code passwort} leer oder {@code null} = unverändert.
 *
 * <p>Sichtbarkeit (beim Hauptpostfach ignoriert):</p>
 * <ul>
 *   <li>{@code sichtbarFuerAlle} {@code null} = bei neuen Postfächern {@code true}, beim Ändern unverändert.</li>
 *   <li>{@code abteilungIds} / {@code benutzerIds} {@code null} = unverändert, leer = alle Freigaben entfernen.
 *       Bei „für alle sichtbar“ werden sie gespeichert, wirken aber nicht.</li>
 * </ul>
 * <p>{@code laeuftAus} {@code null} = unverändert (neu: läuft nicht aus).</p>
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
        Integer imapPort,
        Boolean sichtbarFuerAlle,
        List<Long> abteilungIds,
        List<Long> benutzerIds,
        Boolean laeuftAus) {

    /** Ohne Sichtbarkeits-Angaben (wie in Etappe 1): Sichtbarkeit bleibt unverändert. */
    public PostfachSpeichernRequest(String emailAdresse, String anzeigename, Boolean aktiv, Integer sortierung,
            Boolean hauptpostfach, Boolean fuerGeschaeftsdokumente, String benutzername, String passwort,
            String smtpHost, Integer smtpPort, String imapHost, Integer imapPort) {
        this(emailAdresse, anzeigename, aktiv, sortierung, hauptpostfach, fuerGeschaeftsdokumente, benutzername,
                passwort, smtpHost, smtpPort, imapHost, imapPort, null, null, null, null);
    }
}
