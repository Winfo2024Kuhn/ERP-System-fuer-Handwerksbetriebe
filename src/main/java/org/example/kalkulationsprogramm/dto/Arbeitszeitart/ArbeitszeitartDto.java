package org.example.kalkulationsprogramm.dto.Arbeitszeitart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArbeitszeitartDto {
    private Long id;
    private String bezeichnung;
    private String beschreibung;
    private BigDecimal stundensatz;
    private boolean aktiv;
    private int sortierung;
}
