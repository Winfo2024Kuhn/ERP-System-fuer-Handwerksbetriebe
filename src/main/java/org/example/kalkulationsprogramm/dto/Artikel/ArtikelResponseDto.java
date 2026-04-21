package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Date;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ArtikelResponseDto {
    private Long id;
    private String externeArtikelnummer;
    private String produktlinie;
    private String produktname;
    private String produkttext;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private String waehrung;
    private BigDecimal preis;
    private BigDecimal kgProMeter;
    private Date preisDatum;
    private Long lieferantId;
    private String lieferantenname;
    private List<LieferantPreisDto> lieferantenpreise;
    private int anzahlLieferanten;
    private BigDecimal durchschnittspreisNetto;
    private BigDecimal durchschnittspreisMenge;
    private LocalDateTime durchschnittspreisAktualisiertAm;
    private Long kategorieId;
    private String kategoriePfad;
    private Long parentKategorieId;
    private Long rootKategorieId;
    private String rootKategorieName;
    private Long werkstoffId;
    private String werkstoffName;
    private String kommentar;
    private boolean meterware;
}

