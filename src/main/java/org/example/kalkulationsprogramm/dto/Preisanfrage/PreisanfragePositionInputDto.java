package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Input-DTO fuer eine einzelne Position beim Erstellen einer Preisanfrage.
 * Wird aus dem Bedarf (ArtikelInProjekt) heraus gefuellt.
 */
@Getter
@Setter
public class PreisanfragePositionInputDto {

    /** Optionaler Rueckverweis auf die Original-Bedarfsposition, wichtig fuer Vergabe. */
    private Long artikelInProjektId;

    /** Optional: Rueckverweis auf den Stammartikel. */
    private Long artikelId;

    private String externeArtikelnummer;
    private String produktname;
    private String produkttext;
    private String werkstoffName;
    private BigDecimal menge;
    private String einheit;
    private String kommentar;
    private Integer reihenfolge;
}
