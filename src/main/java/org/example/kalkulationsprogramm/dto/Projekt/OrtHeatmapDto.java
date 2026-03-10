package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrtHeatmapDto {
    private String ort;
    private String plz;
    private long projekte;
    private double umsatz;
    private double anteil;
}
