package org.example.kalkulationsprogramm.dto.Postfach;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Postfach für die Einstellungen. Das Passwort wird nie ausgeliefert – nur, ob eins
 * gesetzt ist.
 *
 * <p>Sichtbarkeit: Beim Hauptpostfach immer {@code sichtbarFuerAlle = true} und leere Listen
 * (das Hauptpostfach sieht jeder im Betrieb). {@code laeuftAus}: Antworten gehen übers
 * Hauptpostfach, für neue Mails nicht mehr wählbar.</p>
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
        List<BenutzerRefDto> zugewieseneBenutzer,
        boolean sichtbarFuerAlle,
        List<AbteilungRefDto> sichtbarFuerAbteilungen,
        List<BenutzerRefDto> sichtbarFuerBenutzer,
        boolean laeuftAus) {

    /** Postfach ohne Sichtbarkeits-Angaben (wie in Etappe 1): für alle sichtbar, läuft nicht aus. */
    public PostfachDto(Long id, String emailAdresse, String anzeigename, boolean aktiv, int sortierung,
            boolean hauptpostfach, boolean fuerGeschaeftsdokumente, String benutzername, boolean passwortGesetzt,
            String smtpHost, Integer smtpPort, String imapHost, Integer imapPort, boolean abrufAktiv,
            LocalDateTime letzterAbrufAm, String letzterAbrufFehler, List<BenutzerRefDto> zugewieseneBenutzer) {
        this(id, emailAdresse, anzeigename, aktiv, sortierung, hauptpostfach, fuerGeschaeftsdokumente, benutzername,
                passwortGesetzt, smtpHost, smtpPort, imapHost, imapPort, abrufAktiv, letzterAbrufAm, letzterAbrufFehler,
                zugewieseneBenutzer, true, List.of(), List.of(), false);
    }

    public record BenutzerRefDto(Long id, String displayName) {
    }

    public record AbteilungRefDto(Long id, String name) {
    }
}
