package org.example.email;

import jakarta.mail.*;
import java.util.Properties;

/**
 * Debug-Utility zum Auflisten aller IMAP-Ordner des Postfachs.
 *
 * <p>Credentials werden aus System-Properties oder Umgebungsvariablen gelesen:
 * <ul>
 *   <li>{@code imap.host} / {@code IMAP_HOST}</li>
 *   <li>{@code imap.username} / {@code IMAP_USERNAME}</li>
 *   <li>{@code imap.password} / {@code IMAP_PASSWORD}</li>
 * </ul>
 * Aufruf z.&nbsp;B.:
 * {@code java -Dimap.host=secureimap.t-online.de -Dimap.username=... -Dimap.password=... org.example.email.ListFolders}
 */
public class ListFolders {

    public static void main(String[] args) {
        String host = requireProperty("imap.host", "IMAP_HOST");
        String username = requireProperty("imap.username", "IMAP_USERNAME");
        String password = requireProperty("imap.password", "IMAP_PASSWORD");

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore();
            store.connect(host, username, password);

            System.out.println("Erfolgreich verbunden. Verfügbare Ordner:");

            Folder defaultFolder = store.getDefaultFolder();
            listFolders(defaultFolder, "");

            store.close();

        } catch (MessagingException e) {
            System.err.println("Fehler beim Auflisten der Ordner: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String requireProperty(String systemPropertyKey, String envKey) {
        String value = System.getProperty(systemPropertyKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Fehlende Konfiguration: -D" + systemPropertyKey + " oder Umgebungsvariable " + envKey
                            + " setzen.");
        }
        return value;
    }

    public static void listFolders(Folder folder, String indent) throws MessagingException {
        System.out.println(indent + "-> " + folder.getFullName());

        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder subFolder : folder.list()) {
                listFolders(subFolder, indent + "  ");
            }
        }
    }
}
