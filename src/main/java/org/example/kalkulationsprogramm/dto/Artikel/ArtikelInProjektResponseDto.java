package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ArtikelInProjektResponseDto {
    private Long id;
    private Long artikelId;
    private String externeArtikelnummer;
    private String produktname;
    private String produkttext;
    private Integer stueckzahl;
    private BigDecimal meter;
    private BigDecimal kilogramm;
    private BigDecimal preisProStueck;
    private LocalDate hinzugefuegtAm;
    private boolean bestellt;
    private LocalDate bestelltAm;
    private String kommentar;
    private String lieferantName;
    private String werkstoffName;
    private String schnittForm;
    private String anschnittWinkelLinks;
    private String anschnittWinkelRechts;
}
