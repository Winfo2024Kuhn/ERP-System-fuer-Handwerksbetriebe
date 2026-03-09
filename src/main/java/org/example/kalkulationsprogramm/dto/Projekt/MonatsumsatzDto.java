package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MonatsumsatzDto {
    private int monat;
    private double letztesJahr;
    private double diesesJahr;
    private double arbeitskosten;
    private double materialkosten;
    private double kosten;
    private double arbeitskostenVorjahr;
    private double materialkostenVorjahr;
    private double kostenVorjahr;
    private double lieferantenkosten; // Eingangsrechnungen von Lieferanten (Netto)
    private double lieferantenkostenVorjahr;
}
