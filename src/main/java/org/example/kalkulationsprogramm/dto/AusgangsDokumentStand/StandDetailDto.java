package org.example.kalkulationsprogramm.dto.AusgangsDokumentStand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;

/**
 * Ein Stand mit seinem vollständigen Inhalt zum Ansehen (Felder wie {@link StandEintragDto} plus Inhalt).
 *
 * @param dokumentBearbeitbar ob das aktuelle Dokument noch geändert werden darf (Wiederherstellen möglich)
 */
public record StandDetailDto(Long id, int nummer, AusgangsDokumentStandArt art, LocalDateTime festgehaltenAm,
        String festgehaltenVon, List<String> bearbeitetVon, BigDecimal betragNetto, List<String> aenderungen,
        Integer wiederhergestelltAusNummer, boolean aktuell, boolean pdfVorhanden,
        String betreff, LocalDate datum, BigDecimal mwstSatz, Integer zahlungszielTage,
        String rechnungsadresseOverride, String positionenJson, String htmlInhalt, boolean dokumentBearbeitbar) {

    public static StandDetailDto aus(StandEintragDto e, String betreff, LocalDate datum, BigDecimal mwstSatz,
            Integer zahlungszielTage, String rechnungsadresseOverride, String positionenJson, String htmlInhalt,
            boolean dokumentBearbeitbar) {
        return new StandDetailDto(e.id(), e.nummer(), e.art(), e.festgehaltenAm(), e.festgehaltenVon(),
                e.bearbeitetVon(), e.betragNetto(), e.aenderungen(), e.wiederhergestelltAusNummer(), e.aktuell(),
                e.pdfVorhanden(), betreff, datum, mwstSatz, zahlungszielTage, rechnungsadresseOverride,
                positionenJson, htmlInhalt, dokumentBearbeitbar);
    }
}
