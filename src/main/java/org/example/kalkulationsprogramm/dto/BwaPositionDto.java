package org.example.kalkulationsprogramm.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BwaPositionDto {
    private Long id;
    private String kontonummer;
    private String bezeichnung;
    private BigDecimal betragMonat;
    private BigDecimal betragKumuliert;
    private String kategorie;
    private Long kostenstelleId;
    private String kostenstelleBezeichnung;
    private Boolean inRechnungenGefunden;
    private BigDecimal rechnungssumme;
    private BigDecimal differenz;
    private Boolean manuellKorrigiert;
    private String notiz;
}
