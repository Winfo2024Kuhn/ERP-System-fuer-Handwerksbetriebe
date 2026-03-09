package org.example.kalkulationsprogramm.dto.Materialkosten;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MaterialkostenResponseDto {
    private Long id;
    private String beschreibung;
    private String externeArtikelnummer;
    private Integer monat;
    private BigDecimal betrag;
    private String rechnungsnummer;
}
