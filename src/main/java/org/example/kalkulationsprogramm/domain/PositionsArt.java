package org.example.kalkulationsprogramm.domain;

import java.util.Locale;

/**
 * Art einer Position auf einem Lieferanten-Dokument.
 *
 * <p>Bei der Projektaufteilung nach Positionen ordnet der Nutzer nur
 * {@link #WARE} zu. Nebenkosten und Rabatte gehören keinem Projekt direkt und
 * werden anteilig nach Warenwert verteilt.
 */
public enum PositionsArt {
    /** Material, Artikel, Dienstleistung – wird einem Projekt zugeordnet. */
    WARE,
    /** Fracht, Verpackung, Maut, Energie-/Legierungszuschlag. */
    NEBENKOSTEN,
    /** Rabatt- oder Abzugszeile (negativer Betrag). */
    RABATT;

    /** Liest die Art aus der KI-Antwort; Unbekanntes zählt als Ware. */
    public static PositionsArt vonText(String text) {
        if (text == null || text.isBlank()) {
            return WARE;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WARE;
        }
    }
}
