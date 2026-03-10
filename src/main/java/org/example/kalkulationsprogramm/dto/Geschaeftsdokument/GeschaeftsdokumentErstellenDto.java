package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO zum Erstellen eines neuen Geschäftsdokuments.
 */
@Getter
@Setter
public class GeschaeftsdokumentErstellenDto {
    private String dokumenttyp; // Enum-Name oder Label des Dokumenttyps
    private LocalDate datum;
    private String betreff;

    private BigDecimal betragNetto;
    private BigDecimal mwstSatz; // z.B. 0.19

    private Long projektId;
    private Long kundeId;
    private Long vorgaengerDokumentId;

    private Integer zahlungszielTage;
    private String htmlInhalt;
}
