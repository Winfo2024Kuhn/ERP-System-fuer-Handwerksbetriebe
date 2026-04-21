package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO für Ausgangs-Geschäftsdokumente.
 */
@Data
public class AusgangsGeschaeftsDokumentResponseDto {
    private Long id;
    private String dokumentNummer;
    private AusgangsGeschaeftsDokumentTyp typ;
    private LocalDate datum;
    private String betreff;
    private String htmlInhalt;
    private String positionenJson;

    // Beträge
    private BigDecimal betragNetto;
    private BigDecimal betragBrutto;
    private BigDecimal mwstSatz;
    private BigDecimal mwstBetrag;
    private Integer abschlagsNummer;
    private Integer zahlungszielTage;
    private LocalDate versandDatum;

    // Status
    private boolean gebucht;
    private LocalDate gebuchtAm;
    private boolean storniert;
    private LocalDate storniertAm;
    private boolean bearbeitbar;

    // Projekt
    private Long projektId;
    private String projektBauvorhaben;
    private String projektnummer;

    // Anfrage
    private Long anfrageId;

    // Kunde (für Rechnungsadresse)
    private Long kundeId;
    private String kundennummer;
    private String kundenName;
    private String rechnungsadresse;

    // Vorgänger
    private Long vorgaengerId;
    private String vorgaengerNummer;

    // Ersteller
    private Long erstelltVonId;
    private String erstelltVonName;

    /**
     * Anzahl Schweißanweisungen, die durch diesen Save über die
     * Leistung↔WPS-Verknüpfung automatisch dem Projekt zugeordnet wurden.
     * Nur bei POST/PUT befüllt; bei GET-Requests null.
     */
    private Integer autoZugewieseneWps;
}
