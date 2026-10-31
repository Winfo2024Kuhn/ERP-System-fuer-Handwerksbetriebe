package org.example.kalkulationsprogramm.dto.Ids;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.service.AufschlagService;

/** Ein Shop-Besuch mit dem zurückgeschickten Warenkorb, angereichert um den eigenen Artikelstamm. */
public record IdsVorgangDto(Long vorgangId, Long lieferantId, String lieferantName, IdsVorgangStatus status,
        String fehlertext, boolean mitBestellung, LocalDateTime empfangenAm, BigDecimal standardAufschlagProzent,
        List<Position> positionen) {

    /**
     * @param einkaufspreis netto je einer Einheit, {@code null} ohne Preis vom Shop
     * @param stamm         passender Artikel im eigenen Artikelstamm (über die Artikelnummer des Lieferanten)
     */
    public record Position(int index, String artikelnummer, String bezeichnung, String langtext, BigDecimal menge,
            String einheit, BigDecimal einkaufspreis, String hinweis, BigDecimal aufschlagProzent,
            AufschlagService.Quelle aufschlagQuelle, Stamm stamm) {
    }

    public record Stamm(Long artikelId, String bezeichnung, BigDecimal aktuellerPreis) {
    }
}
