package org.example.kalkulationsprogramm.dto.Firmendokumente;

/** Neuer Ordner; {@code parentId == null} = oberste Ebene. Prüfung im Service (deutsche Meldungen). */
public record FirmendokumentOrdnerAnlegenDto(String name, Long parentId) {
}
