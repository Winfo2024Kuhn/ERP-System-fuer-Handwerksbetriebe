package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class AnnualAccountingResponseDto {
    private Long mietobjektId;
    private String mietobjektName;
    private String mietobjektStrasse;
    private String mietobjektPlz;
    private String mietobjektOrt;
    private Integer jahr;
    private BigDecimal gesamtkosten;
    private BigDecimal gesamtkostenVorjahr;
    private BigDecimal gesamtkostenDifferenz;
    private List<AnnualAccountingCostCenterDto> kostenstellen = new ArrayList<>();
    private List<AnnualAccountingPartyDto> parteien = new ArrayList<>();
    private List<AnnualAccountingConsumptionDto> verbrauchsvergleiche = new ArrayList<>();
}
