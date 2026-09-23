package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class LieferantArtikelpreisDto {
    private Long artikelId;
    private String produktname;
    private String produkttext;
    private String werkstoff;
    private String externeArtikelnummer;
    private LocalDate preisAenderungsdatum;
    private BigDecimal preis;
    private org.example.kalkulationsprogramm.domain.PreisScope scope;
    private Long projektId;
    private BigDecimal abMenge;
    private BigDecimal bisMenge;
    private LocalDate gueltigAb;
    private LocalDate gueltigBis;
    private String waehrung;
    private String einheit;
    private BigDecimal preisbasisMenge;
    private Long angebotsversionId;
    private Long angebotspositionId;
}
