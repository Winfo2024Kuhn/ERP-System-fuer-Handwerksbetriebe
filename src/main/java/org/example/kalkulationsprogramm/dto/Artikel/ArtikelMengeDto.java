package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtikelMengeDto {
    private Long artikelId;
    private java.math.BigDecimal menge;
    private String einheit;
    private Boolean ausLager;
    private String kommentar;
    private Long lieferantId;
    private java.math.BigDecimal preis;
    // Optional: fuer Profile (Kategorie 1 und Unterkategorien)
    // Erlaubt Fixzuschnitt-Laenge pro Stueck in Metern und eine Stueckzahl anzugeben
    private java.math.BigDecimal laengeProStueck;
    private Integer stueckzahl;
    // Zuschnitt-Details (optional)
    private String schnittForm;
    private String anschnittWinkelLinks;
    private String anschnittWinkelRechts;
}
