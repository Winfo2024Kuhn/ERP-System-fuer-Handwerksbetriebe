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
    /** Bruttobetrag (Gesamtsumme inkl. USt). Für die E-Rechnung zählt {@link #betragNetto}, falls gesetzt. */
    private BigDecimal betrag;
    private String anrede;
    private String kundennummer;
    private String geschaeftsdokumentart; // Rechnung, Anfrage, Auftragsbestätigung

    /**
     * Der rohe UNTDID-1001-Code aus der XML, ungedeutet.
     *
     * <p>{@link #geschaeftsdokumentart} faellt auf "Rechnung" zurueck, wenn weder
     * Dateiname noch TypeCode etwas hergeben - fuer die Einsortierung in der
     * Dokumentliste ist das eine brauchbare Annahme. Wo aus dem Dokumenttyp
     * Kalkulationsdaten werden, darf nicht geraten werden; dort zaehlt dieser
     * Code, und {@code null} heisst dann schlicht "nicht ausgewiesen".
     */
    private String typeCode;

    private Long referenzDokumentId;
    private String mahnstufe;

    // Erweiterte Felder für Zahlungskonditionen
    /** Nettobetrag. Fehlt er, rechnet die E-Rechnung ihn aus {@link #betrag} und {@link #mwstSatz} zurück. */
    private BigDecimal betragNetto;
    /**
     * Steuersatz. Der Extractor liefert ihn als Anteil (0.19), der Dokument-Editor in Prozent (19);
     * {@code ZugferdErstellService} versteht beides.
     */
    private BigDecimal mwstSatz;
    private Boolean bereitsGezahlt = false; // true wenn laut XML schon bezahlt (z.B. Amazon, Vorauskasse)
    private Integer skontoTage;
    private BigDecimal skontoProzent;
    private Integer nettoTage;
    private String bestellnummer;    // Buyer Order Reference (unsere Bestellnummer beim Lieferanten)
    private String referenzNummer;   // Lieferanten-Referenz (AB-Nummer, Projektnummer, Vertragsnummer)

    // Artikelpositionen aus ZUGFeRD-XML
    private List<ZugferdArtikelPosition> artikelPositionen = new ArrayList<>();
}
