package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class KostenpositionDto {
    private Long id;
    private Long kostenstelleId;
    private Integer abrechnungsJahr;
    private BigDecimal betrag;
    private KostenpositionBerechnung berechnung;
    private BigDecimal verbrauchsfaktor;
    private BigDecimal berechneterBetrag;
    private BigDecimal verbrauchsmenge;
    private String beschreibung;
    private String belegNummer;
    private LocalDate buchungsdatum;
    private Long verteilungsschluesselId;
}
