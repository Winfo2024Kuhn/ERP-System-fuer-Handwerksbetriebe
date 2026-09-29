package org.example.kalkulationsprogramm.domain;

/** Wie ein Anruf bzw. eine Sprachnachricht einem Kontakt zugeordnet wurde. */
public enum TelefonZuordnung {
    /** Rufnummer passte eindeutig zu genau einem Kunden oder Lieferanten. */
    AUTOMATISCH,
    /** Von Hand zugeordnet – wird von der Automatik nie überschrieben. */
    MANUELL,
    /** Keinem Kontakt zugeordnet ("Unbekannt"). */
    KEINE
}
