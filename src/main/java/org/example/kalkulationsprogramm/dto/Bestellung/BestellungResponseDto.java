package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class BestellungResponseDto {
    private Long id;
    private Long artikelId;
    private String externeArtikelnummer;
    private String produktname;
    private String produkttext;
    private String werkstoffName;
    private String kategorieName;
    private Integer rootKategorieId;
    private String rootKategorieName;
    private int stueckzahl;
    private java.math.BigDecimal menge;
    private String einheit;
    private Long projektId;
    private String projektName;
    private String projektNummer;
    private String kundenName;
    private String lieferantName;
    private Long lieferantId;
    private boolean bestellt;
    private LocalDate bestelltAm;
    /** Zeitpunkt des letzten Exports (PDF-Download oder E-Mail-Versand). NULL = noch editierbar. */
    private LocalDateTime exportiertAm;
    private String kommentar;
    private java.math.BigDecimal kilogramm;
    private java.math.BigDecimal gesamtKilogramm;
    /** Fixmaß pro Stück in Millimetern (optional). */
    private Integer fixmassMm;
    /** Verpackungseinheit (Stangenlänge in Metern) aus dem Artikelstamm — nur bei Werkstoffen ohne Fixmaß sinnvoll. */
    private Long verpackungseinheit;
    // Schnittbild (Issue #52): ein gemeinsames Bild, zwei Winkel (Grad, Double)
    private Long schnittbildId;
    private String schnittbildBildUrl;
    /** Bild der zugehoerigen Achse — fuer PDF-Plot und Anzeige im Bedarfs-UI. */
    private String schnittAchseBildUrl;
    /** HiCAD-Anschnittbilder: 1:1 aus der Excel-Zelle, unabhängig vom Stamm-Schnittbild. */
    private String anschnittbildStegUrl;
    private String anschnittbildFlanschUrl;
    /** HiCAD-Winkel-Rohtexte (pro Zelle zwei Winkel, z.B. "27.6° 27.6°"). */
    private String anschnittStegText;
    private String anschnittFlanschText;
    private Double anschnittWinkelLinks;
    private Double anschnittWinkelRechts;
    // EN 1090
    private String zeugnisAnforderung;
    private String excKlasse;
    private Integer kategorieId;
    private boolean freiePosition;
}
