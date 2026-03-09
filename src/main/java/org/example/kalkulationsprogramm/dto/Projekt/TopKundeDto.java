package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopKundeDto {
    private String kundenName;
    private Long kundenId;
    private double umsatz;
    private long projektAnzahl;
    private double gewinn;
}
