package org.example.kalkulationsprogramm.dto.Bestellung;

/**
 * Eine Verknüpfung in der Kette.
 *
 * @param vonId Nachfolger, z. B. die Rechnung
 * @param zuId  Vorgänger, z. B. der Lieferschein
 */
public record Verbindung(Long vonId, Long zuId) {
}
