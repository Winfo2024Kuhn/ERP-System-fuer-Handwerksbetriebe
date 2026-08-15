package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

/**
 * Ein Kunde in der Top-Liste der Erfolgsanalyse. Zählt nur, was aus
 * abgeschlossenen Projekten stammt – laufende Projekte bleiben außen vor.
 */
@Getter
@Setter
public class TopKundeDto {
    private String kundenName;
    private Long kundenId;
    /** Summe der Rechnungsbeträge inkl. Umsatzsteuer. */
    private double umsatzBrutto;
    /** Summe der Rechnungsbeträge ohne Umsatzsteuer. */
    private double umsatzNetto;
    private long projektAnzahl;
    private double gewinn;
}
