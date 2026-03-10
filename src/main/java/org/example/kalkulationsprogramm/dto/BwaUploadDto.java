package org.example.kalkulationsprogramm.dto;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.BwaTyp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BwaUploadDto {
    private Long id;
    private BwaTyp typ;
    private Integer jahr;
    private Integer monat;
    private String originalDateiname;
    private String pdfUrl;
    private LocalDateTime uploadDatum;
    private LocalDateTime analyseDatum;
    private Double aiConfidence;
    private Boolean analysiert;
    private Boolean freigegeben;
    private LocalDateTime freigegebenAm;
    private String freigegebenVonName;
    private BigDecimal gesamtGemeinkosten;
    private BigDecimal kostenAusRechnungen;
    private BigDecimal kostenAusBwa;
    private String steuerberaterName;
    private List<BwaPositionDto> positionen;
}
