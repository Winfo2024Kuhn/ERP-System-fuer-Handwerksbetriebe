package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LieferantPreisDto {
    private Long lieferantId;
    private String lieferantName;
    private BigDecimal preis;
}
