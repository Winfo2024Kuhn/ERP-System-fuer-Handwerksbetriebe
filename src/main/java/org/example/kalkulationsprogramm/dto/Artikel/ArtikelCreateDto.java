package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

import java.math.BigDecimal;

@Getter
@Setter
public class ArtikelCreateDto {
    private String produktname;
    private String produktlinie;
    private String produkttext;
    private String externeArtikelnummer;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private Long kategorieId;
    private Long werkstoffId;
    
    // Optional initial price/supplier link
    private Long lieferantId;
    private BigDecimal preis;
}
