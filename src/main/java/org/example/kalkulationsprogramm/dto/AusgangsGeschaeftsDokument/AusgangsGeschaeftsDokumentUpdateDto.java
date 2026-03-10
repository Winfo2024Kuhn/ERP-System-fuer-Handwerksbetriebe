package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO zum Aktualisieren eines bestehenden Dokuments.
 */
@Data
public class AusgangsGeschaeftsDokumentUpdateDto {
    private LocalDate datum;
    private String betreff;
    private BigDecimal betragNetto;
    private BigDecimal mwstSatz;
    private Integer zahlungszielTage;
    private String htmlInhalt;
    private String positionenJson;

    // Rechnungsadresse-Override (nur für dieses Dokument, ändert nicht Kundenstamm)
    private String rechnungsadresseOverride;
}
