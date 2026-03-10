package org.example.kalkulationsprogramm.dto.Produktkategroie;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
}
