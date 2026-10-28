package org.example.kalkulationsprogramm.domain;

/**
 * Was mit einem Büro-Eintrag passiert ist, den eine Handy-Buchung überlappt hat
 * ("Handy gewinnt").
 */
public enum UeberschneidungErgebnis {
    /** Vorne oder hinten so weit gekürzt, dass er die Handy-Buchung nicht mehr überlappt. */
    GEKUERZT,
    /** Er umschloss die Handy-Buchung: Teil davor bleibt, Teil danach ist ein neuer Eintrag. */
    GETEILT,
    /** Er lag ganz in der Handy-Buchung und wurde storniert. */
    ENTFERNT,
    /** Er lief noch und wurde zur Handy-Uhrzeit beendet. */
    BEENDET,
    /** Nicht angefasst, weil der Monat festgeschrieben ist – muss im Büro geklärt werden. */
    NICHT_GEAENDERT,
    /** Nicht angefasst, weil dort, wohin er rücken müsste, schon ein Eintrag beginnt – im Büro klären. */
    ZEIT_BELEGT
}
