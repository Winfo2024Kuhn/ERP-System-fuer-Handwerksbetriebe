package org.example.kalkulationsprogramm.dto.Anfrage;

/**
 * Filter und Sortierung der Anfragen-Übersicht. Alle Werte sind optional;
 * {@code null}, leer oder unbekannt bedeutet „nicht einschränken".
 *
 * @param freigabe   Angebots-Status: all | accepted | pending | expired
 * @param status     offen | beendet | alle
 * @param herkunft   webseite | manuell | alle
 * @param sortierung neu (neueste zuerst, Standard) | alt (älteste zuerst)
 */
public record AnfrageListenFilter(String freigabe, String status, String herkunft, String sortierung) {

    public static AnfrageListenFilter nurFreigabe(String freigabe) {
        return new AnfrageListenFilter(freigabe, null, null, null);
    }
}
