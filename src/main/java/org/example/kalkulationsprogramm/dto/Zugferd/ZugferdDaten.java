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

    // Käuferanschrift (BG-8). Ohne Land gilt Deutschland – EN 16931 verlangt den Ländercode (BR-11).
    private String kundenStrasse;
    private String kundenAdresszusatz;
    private String kundenPlz;
    private String kundenOrt;
    /** Ländercode nach ISO 3166-1 (DE, AT, CH). */
    private String kundenLand;

    private String rechnungsnummer;
    private LocalDate rechnungsdatum;
    private LocalDate faelligkeitsdatum;
    /** Leistungsdatum (BT-72), Pflichtangabe nach §14 UStG. */
    private LocalDate leistungsdatum;
    /**
     * Zahlungsbedingung (BT-20) für Belege ohne Fälligkeit, etwa „Keine Zahlung fällig.“ auf einer
     * Gutschrift. Mit {@link #faelligkeitsdatum} schreibt die E-Rechnung „Zahlbar bis …“.
     */
    private String zahlungsbedingung;
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

    /**
     * Positionen: beim Lesen die Zeilen aus dem ZUGFeRD-XML, beim Erstellen die Rechnungspositionen.
     * Ohne Positionen erstellt {@code ZugferdErstellService} eine Sammelposition über den Nettobetrag.
     */
    private List<ZugferdArtikelPosition> artikelPositionen = new ArrayList<>();

    /** Abzüge auf die ganze Rechnung (Pauschalrabatt, bereits gestellte Rechnungen). Nur beim Erstellen. */
    private List<ZugferdAbzug> abzuege = new ArrayList<>();

    /** Vorausgegangene Rechnungen (BG-3), etwa die Abschläge einer Schlussrechnung. Nur beim Erstellen. */
    private List<ZugferdBelegverweis> vorrechnungen = new ArrayList<>();
}
