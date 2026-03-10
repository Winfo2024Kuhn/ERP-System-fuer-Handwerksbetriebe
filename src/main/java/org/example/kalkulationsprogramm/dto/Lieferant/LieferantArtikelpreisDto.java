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
}
