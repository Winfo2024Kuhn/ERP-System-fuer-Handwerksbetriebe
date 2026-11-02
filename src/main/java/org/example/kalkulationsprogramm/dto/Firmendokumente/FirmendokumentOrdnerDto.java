package org.example.kalkulationsprogramm.dto.Firmendokumente;

/** Ordner im Baum; die Zähler gelten nur für Dokumente direkt in diesem Ordner. */
public record FirmendokumentOrdnerDto(Long id, String name, Long parentId, long anzahlDokumente,
        long anzahlUeberfaellig, long anzahlBaldFaellig) {
}
