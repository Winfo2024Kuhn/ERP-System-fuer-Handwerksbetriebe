package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversionRateDto {
    private int jahr;
    private long anfragenGesamt;
    private long anfragenZuProjekt;
    private double conversionRate;
}
