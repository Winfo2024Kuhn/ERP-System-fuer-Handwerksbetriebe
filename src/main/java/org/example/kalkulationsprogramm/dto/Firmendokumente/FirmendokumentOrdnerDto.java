package org.example.kalkulationsprogramm.dto.Firmendokumente;

/**
 * Ordner im Baum; die Zähler gelten nur für Dokumente direkt in diesem Ordner. {@code darfAendern}: der Angemeldete
 * darf hier hochladen, ändern, löschen und Unterordner pflegen. {@code eingeschraenkt}: der oberste Ordner dieses
 * Zweigs ist auf bestimmte Abteilungen beschränkt (Schloss-Symbol).
 */
public record FirmendokumentOrdnerDto(Long id, String name, Long parentId, long anzahlDokumente,
        long anzahlUeberfaellig, long anzahlBaldFaellig, boolean darfAendern, boolean eingeschraenkt) {
}
