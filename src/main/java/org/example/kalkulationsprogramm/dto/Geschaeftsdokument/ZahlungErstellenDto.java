package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO zum Erstellen einer neuen Zahlung.
 */
@Getter
@Setter
public class ZahlungErstellenDto {
    private LocalDate zahlungsdatum;
    private BigDecimal betrag;
    private String zahlungsart;
    private String verwendungszweck;
    private String notiz;
}
