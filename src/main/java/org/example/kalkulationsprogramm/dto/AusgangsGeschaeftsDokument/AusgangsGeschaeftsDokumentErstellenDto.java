package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO zum Erstellen eines neuen Ausgangs-Geschäftsdokuments.
 */
@Data
public class AusgangsGeschaeftsDokumentErstellenDto {
    private AusgangsGeschaeftsDokumentTyp typ;
    private LocalDate datum;
    private String betreff;
    private BigDecimal betragNetto;
    private BigDecimal mwstSatz;
    private Integer zahlungszielTage;
    private String htmlInhalt;
    private String positionenJson;

    // Verknüpfungen
    private Long projektId;
    private Long angebotId;
    private Long kundeId;
    private Long vorgaengerId;

    // Ersteller
    private Long erstelltVonId;

    // Rechnungsadresse-Override (nur für dieses Dokument, ändert nicht Kundenstamm)
    private String rechnungsadresseOverride;
}
