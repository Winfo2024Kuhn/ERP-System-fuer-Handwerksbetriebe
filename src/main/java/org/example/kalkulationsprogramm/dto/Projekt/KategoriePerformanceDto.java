package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KategoriePerformanceDto {
    private String kategorieName;
    private double umsatz; // Total revenue
    private double gewinn; // Total profit
    private long stueckzahl; // Anzahl der Projekte in dieser Kategorie
    private double umsatzVorjahr;
    private double gewinnVorjahr;
    private long stueckzahlVorjahr;
}
