package org.example.email;

import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;

public class ListFolders {

    private static final Logger log = LoggerFactory.getLogger(ListFolders.class);

    public static void main(String[] args) {
        String username = "info-info@example-company.de"; // Ihre E-Mail-Adresse
        String password = "YOUR_SMTP_PASSWORD";                 // Ihr E-Mail-Passwort

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore();
            store.connect("imap.example.com", username, password);

            log.info("Erfolgreich verbunden. Verfügbare Ordner:");

            // Standard-Namespace des Benutzers abrufen
            Folder defaultFolder = store.getDefaultFolder();

            // Alle Ordner auflisten
            listFolders(defaultFolder, "");

            store.close();

        } catch (MessagingException e) {
            log.error("Fehler beim Auflisten der Ordner: {}", e.getMessage(), e);
        }
    }

    // Hilfsfunktion, um Ordner und Unterordner rekursiv aufzulisten
    public static void listFolders(Folder folder, String indent) throws MessagingException {
        log.info("{} -> {}", indent, folder.getFullName());

        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder subFolder : folder.list()) {
                listFolders(subFolder, indent + "  ");
            }
        }
    }
}
