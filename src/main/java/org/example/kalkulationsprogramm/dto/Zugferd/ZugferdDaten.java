package org.example.kalkulationsprogramm.dto.Zugferd;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ZugferdDaten {
    private String kundenName;
    private String email;
    private String rechnungsnummer;
    private LocalDate rechnungsdatum;
    private LocalDate faelligkeitsdatum;
    private BigDecimal betrag;
    private String anrede;
    private String kundennummer;
    private String geschaeftsdokumentart; // Rechnung, Angebot, Auftragsbestätigung
    private Long referenzDokumentId;
    private String mahnstufe;

    // Erweiterte Felder für Zahlungskonditionen
    private BigDecimal betragNetto;
    private BigDecimal mwstSatz;
    private Integer skontoTage;
    private BigDecimal skontoProzent;
    private Integer nettoTage;
    private String bestellnummer;    // Buyer Order Reference (unsere Bestellnummer beim Lieferanten)
    private String referenzNummer;   // Lieferanten-Referenz (AB-Nummer, Projektnummer, Vertragsnummer)

    // Artikelpositionen aus ZUGFeRD-XML
    private List<ZugferdArtikelPosition> artikelPositionen = new ArrayList<>();
}
