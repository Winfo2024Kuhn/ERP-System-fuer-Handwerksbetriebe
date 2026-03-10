package org.example.kalkulationsprogramm.dto.Leistung;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

import java.math.BigDecimal;

@Data
public class LeistungCreateDto {
    private String name;
    private String description;
    private BigDecimal price;
    private Verrechnungseinheit unit;
    private Long folderId;
}
