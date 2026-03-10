package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsart;

import java.math.BigDecimal;

@Data
public class AnnualAccountingConsumptionDto {
    private Long verbrauchsgegenstandId;
    private String name;
    private String raumName;
    private Verbrauchsart verbrauchsart;
    private String einheit;
    private BigDecimal verbrauchJahr;
    private BigDecimal verbrauchVorjahr;
    private BigDecimal differenz;
}
