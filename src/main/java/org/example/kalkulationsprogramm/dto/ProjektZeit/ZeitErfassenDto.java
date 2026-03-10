package org.example.kalkulationsprogramm.dto.ProjektZeit;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ZeitErfassenDto {
    private Long arbeitsgangID;
    private Long produktkategorieID;
    private BigDecimal anzahlInStunden;
}
