package org.example.kalkulationsprogramm.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FirmeninformationDto {
    private Long id;
    private String firmenname;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String fax;
    private String email;
    private String website;
    private String steuernummer;
    private String ustIdNr;
    private String handelsregister;
    private String handelsregisterNummer;
    private String bankName;
    private String iban;
    private String bic;
    private String logoDateiname;
    private String geschaeftsfuehrer;
    private String fusszeileText;
    private String googleBewertungsLink;
    /** Hausfarbe als Hex mit fuehrendem #, z.B. "#500010". Leer = Standardfarbe. */
    private String firmenfarbe;

    // Mahnverfahren — siehe Firmeninformation
    private boolean mahnverfahrenAktiv;
    private int tageBisZahlungserinnerung;
    private int tageBisErsteMahnung;
    private int tageBisZweiteMahnung;
    private int mahnverfahrenNeuesZahlungszielTage;

    // Gewerk + Unfallversicherung
    private Long gewerkId;
    private String gewerkName;
    private String bgName;
    private BigDecimal bgSatzVorschlag;   // Default vom Gewerk
    private BigDecimal bgSatzOverride;    // Manuelle Ueberschreibung aus dem Beitragsbescheid
    private BigDecimal bgSatzEffektiv;    // override > vorschlag (was die Berechnung tatsaechlich nutzt)
}
