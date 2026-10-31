package org.example.kalkulationsprogramm.service.ids;

import java.math.BigDecimal;
import java.util.List;

/** Ein vom Shop zurückgeschickter Warenkorb, unabhängig vom Protokoll. */
public record IdsWarenkorb(boolean mitBestellung, List<Zeile> zeilen) {

    public IdsWarenkorb {
        zeilen = List.copyOf(zeilen);
    }

    /**
     * @param einkaufspreis netto je einer Einheit; {@code null}, wenn der Shop keinen Preis geschickt hat
     * @param einheit       schon in deutscher Kurzform
     */
    public record Zeile(String artikelnummer, String bezeichnung, String langtext, BigDecimal menge,
            String einheit, BigDecimal einkaufspreis, String hinweis, String ean) {
    }
}
