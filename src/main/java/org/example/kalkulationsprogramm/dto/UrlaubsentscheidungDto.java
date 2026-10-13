package org.example.kalkulationsprogramm.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Urlaubsantrag;

/**
 * Eine Entscheidung des Büros zu einem eigenen Antrag (genehmigt, abgelehnt,
 * storniert) für die Glocke der Handy-App. Bewusst ohne Mitarbeiterdaten – die
 * App fragt ohnehin nur die eigenen Entscheidungen ab.
 */
public record UrlaubsentscheidungDto(Long id, Urlaubsantrag.Typ typ, Urlaubsantrag.Status status,
        LocalDate vonDatum, LocalDate bisDatum, LocalDateTime entschiedenAm) {

    public static UrlaubsentscheidungDto from(Urlaubsantrag antrag) {
        return new UrlaubsentscheidungDto(antrag.getId(), antrag.getTyp(), antrag.getStatus(),
                antrag.getVonDatum(), antrag.getBisDatum(), antrag.getEntschiedenAm());
    }
}
