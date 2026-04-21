package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class LieferantPreisDto {
    private Long lieferantId;
    private String lieferantName;
    private BigDecimal preis;
    private String externeArtikelnummer;
    private Date preisDatum;
}
