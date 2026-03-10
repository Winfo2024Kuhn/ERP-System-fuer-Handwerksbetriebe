package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class AnnualAccountingCostCenterDto {
    private Long kostenstelleId;
    private String kostenstelleName;
    private BigDecimal summe;
    private BigDecimal vorjahr;
    private BigDecimal differenz;
    private List<AnnualAccountingShareDto> parteianteile = new ArrayList<>();
}
