package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RaumDto {
    private Long id;
    private Long mietobjektId;
    private String name;
    private String beschreibung;
    private BigDecimal flaecheQuadratmeter;
}
