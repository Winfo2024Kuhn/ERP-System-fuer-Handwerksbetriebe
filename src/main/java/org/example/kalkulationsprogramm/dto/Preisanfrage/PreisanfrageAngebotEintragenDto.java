package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Input-DTO fuer das manuelle Eintragen eines Angebotspreises eines Lieferanten
 * zu einer einzelnen Position einer Preisanfrage.
 */
@Getter
@Setter
public class PreisanfrageAngebotEintragenDto {

    /** PreisanfrageLieferant, der geantwortet hat. */
    private Long preisanfrageLieferantId;

    /** Position, fuer die der Preis gilt. */
    private Long preisanfragePositionId;

    private BigDecimal einzelpreis;
    private BigDecimal gesamtpreis;
    private BigDecimal mwstProzent;
    private Integer lieferzeitTage;
    private LocalDate gueltigBis;
    private String bemerkung;
}
