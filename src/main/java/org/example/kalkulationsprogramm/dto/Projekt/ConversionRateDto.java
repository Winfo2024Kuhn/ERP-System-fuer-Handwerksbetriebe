package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversionRateDto {
    private int jahr;
    private long angeboteGesamt;
    private long angeboteZuProjekt;
    private double conversionRate;
}
