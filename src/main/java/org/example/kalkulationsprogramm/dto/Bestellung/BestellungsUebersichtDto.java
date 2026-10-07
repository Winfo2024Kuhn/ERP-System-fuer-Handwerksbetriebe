package org.example.kalkulationsprogramm.dto.Bestellung;

import java.util.List;

/**
 * Die Bestellübersicht: alle Dokumenten-Ketten, gruppiert nach Status.
 */
public record BestellungsUebersichtDto(
        List<DokumentenKette> offeneAnfragen,
        List<DokumentenKette> laufendeBestellungen,
        List<DokumentenKette> abgeschlossen,
        List<DokumentenKette> zugeordnet,
        List<DokumentenKette> ausgeblendet) {
}
