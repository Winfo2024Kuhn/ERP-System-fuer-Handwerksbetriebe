package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KategorieUmsatzVergleichDto {
    private String kategorie;
    private long letztesJahr;
    private long diesesJahr;
    private String verrechnungseinheit;
}
