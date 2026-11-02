package org.example.kalkulationsprogramm.dto.Firmendokumente;

/** Ergebnis der Zähl-Abfrage je Ordner (nur direkt enthaltene Dokumente). */
public record FirmendokumentOrdnerZaehler(Long ordnerId, Long anzahl, Long ueberfaellig, Long baldFaellig) {
}
