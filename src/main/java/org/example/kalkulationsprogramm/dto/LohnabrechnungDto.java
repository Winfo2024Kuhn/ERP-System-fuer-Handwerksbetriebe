package org.example.kalkulationsprogramm.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO für Lohnabrechnung-Anzeige im Frontend.
 */
@Getter
@Setter
public class LohnabrechnungDto {
    private Long id;
    private Long mitarbeiterId;
    private String mitarbeiterName;
    private Long steuerberaterId;
    private String steuerberaterName;
    private Integer jahr;
    private Integer monat;
    private String originalDateiname;
    private String downloadUrl;
    private BigDecimal bruttolohn;
    private BigDecimal nettolohn;
    private LocalDateTime importDatum;
    private String status;
}
