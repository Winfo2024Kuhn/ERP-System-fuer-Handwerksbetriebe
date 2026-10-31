package org.example.kalkulationsprogramm.service.ki;

/**
 * Was ein KI-Anbieter in diesem Programm kann. Eine Fähigkeit zählt erst,
 * wenn sie in der {@link KiAnbieter}-Umsetzung auch gebaut ist – nicht schon,
 * wenn der Anbieter sie irgendwo anbietet.
 */
public enum KiFaehigkeit {
    TEXT("Text"),
    BILD("Bilder"),
    PDF("PDF-Dokumente"),
    AUDIO("Sprache"),
    /** Function Calling: die KI schlägt über mehrere Runden selbst im Programm nach. */
    WERKZEUGE("im Programm nachschlagen"),
    /**
     * Die KI sucht selbst im Internet. Keine Funktion braucht sie zwingend; wer sie nicht
     * kann, antwortet ohne ({@link KiFunktion#nutztWennMoeglich()}).
     */
    WEBSUCHE("Internet-Suche");

    private final String bezeichnung;

    KiFaehigkeit(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    /** Bezeichnung in Handwerker-Sprache für Hinweise in der Oberfläche. */
    public String bezeichnung() {
        return bezeichnung;
    }
}
