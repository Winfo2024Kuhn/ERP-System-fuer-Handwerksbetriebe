package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;

import java.math.BigDecimal;

@Data
public class AnnualAccountingShareDto {
    private Long mietparteiId;
    private String mietparteiName;
    private MietparteiRolle rolle;
    private BigDecimal betrag;
}
