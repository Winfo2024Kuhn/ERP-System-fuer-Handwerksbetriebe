package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO für Geschäftsdokument mit berechneten Feldern.
 */
@Getter
@Setter
public class GeschaeftsdokumentResponseDto {
    private Long id;
    private Long dokumenttypId;
    private String dokumenttypName; // z.B. "Angebot", "Auftragsbestätigung"
    private String dokumentNummer;
    private LocalDate datum;
    private LocalDate versandDatum;

    private BigDecimal betragNetto;
    private BigDecimal betragBrutto;
    private BigDecimal mwstSatz;
    private BigDecimal mwstBetrag;

    private Integer abschlagsNummer;
    private String betreff;
    private Integer zahlungszielTage;
    private boolean storniert;

    // Beziehungen
    private Long projektId;
    private String projektBauvorhaben;
    private Long kundeId;
    private String kundenName;

    // Vorgänger-Info
    private VorgaengerInfoDto vorgaenger;

    // Berechnete Felder
    private BigDecimal summeZahlungen;
    private BigDecimal offenerBetrag;
    private boolean bezahlt;

    // Zahlungen
    private List<ZahlungDto> zahlungen;

    // Für Abschluss-Berechnung bei Rechnungen
    private AbschlussInfoDto abschlussInfo;
}
