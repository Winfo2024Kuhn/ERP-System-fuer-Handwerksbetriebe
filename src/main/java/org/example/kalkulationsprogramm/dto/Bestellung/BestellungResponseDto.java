package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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
    private String kommentar;
    private java.math.BigDecimal kilogramm;
    private java.math.BigDecimal gesamtKilogramm;
    /** Fixmaß pro Stück in Millimetern (optional). */
    private Integer fixmassMm;
    private String schnittForm;
    private String anschnittWinkelLinks;
    private String anschnittWinkelRechts;
    // EN 1090
    private String zeugnisAnforderung;
    private String excKlasse;
    private Integer kategorieId;
    private boolean freiePosition;
}
