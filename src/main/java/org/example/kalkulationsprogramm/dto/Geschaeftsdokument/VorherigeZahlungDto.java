package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO für eine vorherige Zahlung (aus anderen Dokumenten der gleichen Kette).
 */
@Getter
@Setter
public class VorherigeZahlungDto {
    private String dokumentNummer; // z.B. "2024-AR-0001"
    private String dokumentTypAnzeigename; // z.B. "1. Abschlagsrechnung"
    private BigDecimal betrag;
}
