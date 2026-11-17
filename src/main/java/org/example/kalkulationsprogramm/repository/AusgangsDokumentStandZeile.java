package org.example.kalkulationsprogramm.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;

/** Listenzeile eines Stands ohne Positionen und HTML (Projektion für die Seitenabfrage). */
public record AusgangsDokumentStandZeile(Long id, Integer nummer, AusgangsDokumentStandArt art,
        LocalDateTime festgehaltenAm, Long festgehaltenVonId, String bearbeitetVonIds, BigDecimal betragNetto,
        String aenderungen, Integer wiederhergestelltAusNummer, String inhaltFingerabdruck, String pdfDateiname) {
}
