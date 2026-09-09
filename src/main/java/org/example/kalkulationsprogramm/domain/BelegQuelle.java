package org.example.kalkulationsprogramm.domain;

/**
 * Herkunft eines Belegs -- steuert die Anzeige im Kassenbuch (z.B. ob ein
 * Beleg-Foto erwartet wird), nicht die buchhalterische Kategorie.
 */
public enum BelegQuelle {
    /** Foto/Upload -- der Handwerker hat einen Beleg abfotografiert oder hochgeladen. */
    SCAN,
    /** Vom Programm automatisch erzeugte Kundenquittung. */
    QUITTUNG,
    /** Vom Programm erzeugter Ersatzbeleg, wenn kein Originalbeleg vorliegt. */
    EIGENBELEG,
    /** Bank zu Kasse/Privat ohne Fremdbeleg (Umbuchung). */
    TRANSFER
}
