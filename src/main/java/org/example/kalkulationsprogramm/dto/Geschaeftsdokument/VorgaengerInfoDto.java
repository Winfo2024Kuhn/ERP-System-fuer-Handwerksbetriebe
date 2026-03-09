package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO für Vorgänger-Dokument-Informationen.
 */
@Getter
@Setter
public class VorgaengerInfoDto {
    private Long id;
    private Long dokumenttypId;
    private String dokumenttypName;
    private String dokumentNummer;
    private BigDecimal betragBrutto;
}
