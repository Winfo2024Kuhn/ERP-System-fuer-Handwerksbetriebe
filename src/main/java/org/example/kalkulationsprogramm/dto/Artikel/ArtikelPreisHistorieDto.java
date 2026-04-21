package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ArtikelPreisHistorieDto {
    private Long id;
    private BigDecimal preis;
    private BigDecimal menge;
    private Verrechnungseinheit einheit;
    private PreisQuelle quelle;
    private Long lieferantId;
    private String lieferantName;
    private String externeNummer;
    private String belegReferenz;
    private LocalDateTime erfasstAm;
    private String bemerkung;
}
