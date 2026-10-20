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
    /** Betriebseigene Artikelnummer (Spalte „Artikel-Nr.“), optional, eindeutig. */
    private String artikelnummer;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private Long kategorieId;
    private Long werkstoffId;
    
    // Optional: erster Preis beim Lieferanten. Preis und externe Nummer nur mit Lieferant.
    private Long lieferantId;
    private BigDecimal preis;
}
