package org.example.kalkulationsprogramm.dto.Ids;

import java.math.BigDecimal;
import java.util.List;

/** Welche Warenkorb-Zeilen den Artikelstamm ändern sollen. */
public record IdsArtikelstammAnfrageDto(List<Aktualisieren> aktualisieren, List<NeuAnlegen> neuAnlegen) {

    /** Bekannten Artikel: neuen Einkaufspreis für diesen Lieferanten eintragen. */
    public record Aktualisieren(Integer index, BigDecimal einkaufspreis) {
    }

    /** Unbekannte Artikelnummer: neuen Artikel anlegen. */
    public record NeuAnlegen(Integer index, BigDecimal einkaufspreis, String bezeichnung) {
    }
}
