package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ArbeitsgangResponseDto {
    private Long id;
    private String beschreibung;
    private BigDecimal stundensatz;
    private Long abteilungId;
    private String abteilungName;
    private Integer stundensatzJahr;
}
