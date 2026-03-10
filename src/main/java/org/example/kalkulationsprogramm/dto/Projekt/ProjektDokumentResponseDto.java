package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ProjektDokumentResponseDto {
    private Long id;
    private String originalDateiname;
    private String dateityp;
    private String url;
    private String netzwerkPfad;
    private String dokumentGruppe;
    private LocalDate uploadDatum;
    private LocalDate emailVersandDatum;

    private String anrede;
    private String rechnungsnummer;
    private LocalDate rechnungsdatum;
    private LocalDate faelligkeitsdatum;
    private String geschaeftsdokumentart; // Rechnung, Angebot, Auftragsbestätigung, Mahnung
    private String mahnstufe;
    private Long referenzDokumentId;
    private String referenzDokumentNummer;
    private boolean mahnung;
    private BigDecimal rechnungsbetrag;
    private boolean bezahlt;
    private Long projektId;
    private String projektAuftragsnummer;
    private String projektKunde;
    private String projektKategorie;
    private BigDecimal projektArbeitskosten;
    private BigDecimal projektMaterialkosten;
    private BigDecimal projektKosten;
    
    // Lieferant Zuordnung
    private Long lieferantId;
    private String lieferantenname;
    
    // Upload-Tracking: Wer hat das Dokument hochgeladen?
    private String uploadedByVorname;
    private String uploadedByNachname;
}

