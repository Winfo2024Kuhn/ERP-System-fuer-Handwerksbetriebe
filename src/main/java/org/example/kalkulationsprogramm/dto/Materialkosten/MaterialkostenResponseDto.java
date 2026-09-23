package org.example.kalkulationsprogramm.dto.Materialkosten;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;

@Getter
@Setter
public class MaterialkostenResponseDto {
    private Long id;
    private String beschreibung;
    private String externeArtikelnummer;
    private Integer monat;
    private BigDecimal betrag;
    private String rechnungsnummer;
    private Long artikelIdSnapshot;
    private Long lieferantenArtikelPreisId;
    private String lieferantennameSnapshot;
    private BigDecimal mengeSnapshot;
    private Einheit einheitSnapshot;
    private BigDecimal preisJeEinheitSnapshot;
    private String preisquelleSnapshot;
    private String preisnotizSnapshot;
}
