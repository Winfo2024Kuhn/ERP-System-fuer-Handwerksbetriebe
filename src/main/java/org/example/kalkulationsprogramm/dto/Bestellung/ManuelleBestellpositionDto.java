package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ManuelleBestellpositionDto {
    private Long projektId;
    private Long lieferantId;
    private Integer kategorieId;
    private String produktname;
    private String produkttext;
    private BigDecimal menge;
    private String einheit;
    private String zeugnisAnforderung;
    private String kommentar;
}
