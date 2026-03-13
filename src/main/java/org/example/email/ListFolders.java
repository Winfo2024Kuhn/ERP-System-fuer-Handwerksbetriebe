package org.example.email;

import jakarta.mail.*;
import java.util.Properties;

public class ListFolders {

    public static void main(String[] args) {
        String username = "info-bauschlosserei-kuhn@t-online.de"; // Ihre E-Mail-Adresse
        String password = "Lini+marviTkom";                 // Ihr E-Mail-Passwort

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore();
            store.connect("secureimap.t-online.de", username, password);

            System.out.println("Erfolgreich verbunden. Verfügbare Ordner:");

            // Standard-Namespace des Benutzers abrufen
            Folder defaultFolder = store.getDefaultFolder();

            // Alle Ordner auflisten
            listFolders(defaultFolder, "");

            store.close();

        } catch (MessagingException e) {
            System.err.println("Fehler beim Auflisten der Ordner: " + e.getMessage());
            e.printStackTrace();
        }
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
