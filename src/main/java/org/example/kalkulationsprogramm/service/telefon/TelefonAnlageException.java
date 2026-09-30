package org.example.kalkulationsprogramm.service.telefon;

/**
 * Fehler beim Zugriff auf die Telefonanlage. Die Nachricht ist für Nutzer
 * gedacht ("FRITZ!Box nicht erreichbar") und enthält keine Personendaten.
 */
public class TelefonAnlageException extends RuntimeException {

    public enum Grund {
        NICHT_ERREICHBAR("FRITZ!Box nicht erreichbar"),
        ANMELDUNG_FEHLGESCHLAGEN("Benutzername oder Passwort falsch"),
        KEINE_RECHTE("Der FRITZ!Box-Benutzer hat nicht die nötigen Rechte"),
        UNERWARTETE_ANTWORT("Unerwartete Antwort der FRITZ!Box"),
        NICHT_EINGERICHTET("Telefon-Anbindung ist nicht eingerichtet"),
        WAEHLHILFE_AUS("Die FRITZ!Box konnte nicht wählen. Bitte prüfen, ob die Wählhilfe eingeschaltet ist "
                + "(FRITZ!Box: Telefonie → Anrufe → Wählhilfe)."),
        BESCHAEFTIGT("Es wird gerade schon ein Anruf aufgebaut. Bitte gleich noch einmal versuchen.");

        private final String text;

        Grund(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    private final Grund grund;

    public TelefonAnlageException(Grund grund) {
        super(grund.text());
        this.grund = grund;
    }

    public TelefonAnlageException(Grund grund, Throwable ursache) {
        super(grund.text(), ursache);
        this.grund = grund;
    }

    public Grund getGrund() {
        return grund;
    }
}
