package org.example.kalkulationsprogramm.dto.Produktkategroie;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProduktkategorieAnalyseDto {
    private long projektAnzahl;
    private double durchschnittlicheZeit;
    private double fixzeit;
    private double steigung;
    private String verrechnungseinheit;
    private List<ProjektAnalyseDto> projekte;
    private List<ArbeitsgangAnalyseDto> arbeitsgangAnalysen;
    private int datenpunkte;
    @JsonProperty("rQuadrat")
    private double rQuadrat;
    private double residualStdAbweichung;
}
