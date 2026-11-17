package org.example.kalkulationsprogramm.dto.AusgangsDokumentStand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;

/**
 * Ein Stand im Verlauf eines Ausgangsdokuments.
 *
 * @param bearbeitetVon             Anzeigenamen derer, die seit dem vorigen Stand gespeichert haben
 * @param aenderungen               Sätze gegenüber dem vorigen Stand („Preis Pos. 2: 120,00 € → 95,00 €“)
 * @param wiederhergestelltAusNummer gesetzt, wenn seit dem vorigen Stand dieser Stand zurückgeholt wurde
 * @param aktuell                   Inhalt gleicht dem aktuellen Dokumentinhalt
 * @param pdfVorhanden              das PDF dieses Exports liegt noch vor ({@code …/staende/{standId}/pdf})
 */
public record StandEintragDto(Long id, int nummer, AusgangsDokumentStandArt art, LocalDateTime festgehaltenAm,
        String festgehaltenVon, List<String> bearbeitetVon, BigDecimal betragNetto, List<String> aenderungen,
        Integer wiederhergestelltAusNummer, boolean aktuell, boolean pdfVorhanden) {
}
