package org.example.kalkulationsprogramm.service.mail;

import java.util.Properties;

import org.example.kalkulationsprogramm.service.SystemSettingsService;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * Öffnet eine IMAP-Verbindung (immer verschlüsselt) mit denselben Zeitgrenzen wie der Abruf.
 * Der Aufrufer schließt den {@link Store} selbst (try-with-resources).
 */
final class ImapVerbindung {

    private ImapVerbindung() {
    }

    static Store verbinde(SystemSettingsService.ImapZugang zugang) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.mime.address.strict", "false");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");

        Store store = Session.getInstance(props).getStore("imaps");
        store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());
        return store;
    }
}
