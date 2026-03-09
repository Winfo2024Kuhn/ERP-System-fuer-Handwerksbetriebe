package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO für Zahlungsinformationen.
 */
@Getter
@Setter
public class ZahlungDto {
    private Long id;
    private LocalDate zahlungsdatum;
    private BigDecimal betrag;
    private String zahlungsart;
    private String verwendungszweck;
    private String notiz;

    // Für Abschluss-Anzeige
    private String dokumentNummer; // Referenz auf das bezahlte Dokument
    private String dokumentTypAnzeigename;
}
