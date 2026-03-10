package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;

import java.math.BigDecimal;

@Data
public class MietparteiDto {
    private Long id;
    private String name;
    private MietparteiRolle rolle;
    private String email;
    private String telefon;
    private BigDecimal monatlicherVorschuss;
}
