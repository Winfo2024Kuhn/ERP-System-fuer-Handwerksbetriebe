package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VerteilungsschluesselEintragDto {
    private Long id;
    private Long mietparteiId;
    private Long verbrauchsgegenstandId;
    private BigDecimal anteil;
    private String kommentar;
}
