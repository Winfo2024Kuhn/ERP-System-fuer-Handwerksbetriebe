package org.example.kalkulationsprogramm.domain;

/** Was mit einem Kunden aus Datenschutzgründen passiert ist (siehe {@link KundeLoeschprotokoll}). */
public enum KundeLoeschAktion {
    /** Kunde ohne aufbewahrungspflichtige Unterlagen wurde komplett gelöscht. */
    GELOESCHT,
    /** Nicht nötige Daten gelöscht, Kunde bis zum Fristende gesperrt. */
    GESPERRT,
    /** Gesperrter Kunde nach Fristende anonymisiert: Name und Anschrift entfernt. */
    ANONYMISIERT
}
