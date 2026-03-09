package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UmsatzStatistikDto {
    private List<KategorieUmsatzVergleichDto> kategorien;
    private List<MonatsumsatzDto> monatsUmsaetze;
    private ConversionRateDto konversion;
    private List<OrtHeatmapDto> ortHeatmap;
    private List<KategoriePerformanceDto> kategoriePerformance;
    private List<TopKundeDto> topKunden;
}
