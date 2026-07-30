package org.example.email;

import jakarta.mail.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Diagnose-Werkzeug: listet alle IMAP-Ordner des konfigurierten Postfachs auf.
 *
 * <p>Nuetzlich, um den tatsaechlichen Namen des "Gesendet"-Ordners eines
 * Providers herauszufinden — den braucht der {@code SentMailArchiver}, und er
 * heisst je nach Anbieter {@code INBOX.Sent}, {@code Sent} oder lokalisiert
 * {@code INBOX.Gesendete Objekte}.</p>
 *
 * <p><strong>Zugangsdaten:</strong> werden aus
 * {@code src/main/resources/application-local.properties} gelesen (gitignored).
 * Es stehen bewusst keine Zugangsdaten im Code — die laufende Anwendung nutzt
 * ohnehin {@code SystemSettingsService}, das die Werte aus der Datenbank liest
 * und nur ersatzweise auf die Properties zurueckfaellt.</p>
 */
public class ListFolders {

    private static final Path LOCAL_PROPERTIES =
            Path.of("src", "main", "resources", "application-local.properties");

    public static void main(String[] args) {
        Properties config;
        try {
            config = ladeLokaleKonfiguration();
        } catch (IOException e) {
            System.err.println("Konnte " + LOCAL_PROPERTIES + " nicht lesen: " + e.getMessage());
            System.err.println("Bitte aus dem Projektstamm heraus starten.");
            return;
        }

        String username = config.getProperty("spring.mail.imap.username",
                config.getProperty("spring.mail.username", ""));
        String password = config.getProperty("spring.mail.imap.password",
                config.getProperty("spring.mail.password", ""));
        String host = config.getProperty("spring.mail.imap.host", "secureimap.t-online.de");

        if (username.isBlank() || password.isBlank()) {
            System.err.println("Keine IMAP-Zugangsdaten in " + LOCAL_PROPERTIES + " gefunden.");
            System.err.println("Erwartet: spring.mail.imap.username / spring.mail.imap.password");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore();
            store.connect(host, username, password);

            System.out.println("Erfolgreich verbunden. Verfügbare Ordner:");
            listFolders(store.getDefaultFolder(), "");

            store.close();

        } catch (MessagingException e) {
            System.err.println("Fehler beim Auflisten der Ordner: " + e.getMessage());
        }
    }

    private static Properties ladeLokaleKonfiguration() throws IOException {
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(LOCAL_PROPERTIES)) {
            config.load(in);
        }
        return config;
    }

    // Hilfsfunktion, um Ordner und Unterordner rekursiv aufzulisten
    public static void listFolders(Folder folder, String indent) throws MessagingException {
        System.out.println(indent + "-> " + folder.getFullName());

        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder subFolder : folder.list()) {
                listFolders(subFolder, indent + "  ");
            }
        }
    }
}
