package org.example.kalkulationsprogramm.service.mail;

import org.example.kalkulationsprogramm.service.SystemSettingsService;

/**
 * Entschlüsselter Zugang eines Postfachs – lebt nur im Speicher, wird nie
 * gespeichert oder ausgeliefert.
 *
 * @param postfachId {@code null} beim Rückfall auf das Konto aus den System-Einstellungen
 */
public record PostfachZugang(
        Long postfachId,
        String emailAdresse,
        String anzeigename,
        String benutzername,
        String passwort,
        String smtpHost,
        int smtpPort,
        String imapHost,
        int imapPort,
        boolean hauptpostfach) {

    public static final int STANDARD_SMTP_PORT = 465;
    public static final int STANDARD_IMAP_PORT = 993;

    /** Benutzername, Passwort und SMTP-Server vorhanden. */
    public boolean hatVersandZugang() {
        return gesetzt(benutzername) && gesetzt(passwort) && gesetzt(smtpHost);
    }

    /** Benutzername, Passwort und IMAP-Server vorhanden. */
    public boolean hatAbrufZugang() {
        return gesetzt(benutzername) && gesetzt(passwort) && gesetzt(imapHost);
    }

    public SystemSettingsService.MailKonto mailKonto() {
        return new SystemSettingsService.MailKonto(smtpHost, smtpPort, benutzername, passwort,
                emailAdresse, anzeigename == null ? "" : anzeigename);
    }

    public SystemSettingsService.ImapZugang imapZugang() {
        return new SystemSettingsService.ImapZugang(imapHost, imapPort, benutzername, passwort);
    }

    /** Ohne Passwort – ein versehentlich geloggter Zugang verrät es nicht. */
    @Override
    public String toString() {
        return "PostfachZugang[postfachId=" + postfachId + ", smtpHost=" + smtpHost + ", smtpPort=" + smtpPort
                + ", imapHost=" + imapHost + ", imapPort=" + imapPort + ", hauptpostfach=" + hauptpostfach
                + ", passwort=" + (gesetzt(passwort) ? "***" : "") + "]";
    }

    private static boolean gesetzt(String wert) {
        return wert != null && !wert.isBlank();
    }
}
