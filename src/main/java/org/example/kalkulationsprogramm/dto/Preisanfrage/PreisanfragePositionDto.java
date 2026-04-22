package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ausgabe-DTO fuer eine Position innerhalb einer Preisanfrage.
 * Wird in {@link PreisanfrageResponseDto} verwendet.
 */
@Getter
@Setter
@NoArgsConstructor
public class PreisanfragePositionDto {

    private Long id;
    private Integer reihenfolge;
    private Long artikelInProjektId;
    private Long artikelId;
    private String externeArtikelnummer;
    private String produktname;
    private String produkttext;
    private String werkstoffName;
    private BigDecimal menge;
    private String einheit;
    private String kommentar;

    // Schnittbild + zwei Anschnittwinkel (Issue #52)
    private Long schnittbildId;
    private String schnittbildForm;
    private String schnittbildBildUrl;
    private Double anschnittWinkelLinks;
    private Double anschnittWinkelRechts;
}
