package org.example.kalkulationsprogramm.dto.Leistung;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;

/** In welchen Ausgangsdokumenten eine Leistung steckt (Liste gekürzt, Zähler vollständig). */
public record LeistungVerwendungDto(long dokumentAnzahl, List<Dokument> dokumente) {

    public record Dokument(Long id, String dokumentNummer, AusgangsGeschaeftsDokumentTyp typ, LocalDate datum,
            Long projektId, String bauvorhaben) {
    }
}
