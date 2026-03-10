package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ZaehlerstandDto {
    private Long id;
    private Long verbrauchsgegenstandId;
    private Integer abrechnungsJahr;
    private LocalDate stichtag;
    private BigDecimal stand;
    private BigDecimal verbrauch;
    private String kommentar;
}
