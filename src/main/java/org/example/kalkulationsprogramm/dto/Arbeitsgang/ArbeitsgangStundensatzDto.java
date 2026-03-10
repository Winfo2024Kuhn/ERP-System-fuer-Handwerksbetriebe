package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ArbeitsgangStundensatzDto {
    private Long arbeitsgangId;
    private BigDecimal stundensatz;
}
