package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ManuelleBestellpositionDto {
    private Long projektId;
    private Long lieferantId;
    private Integer kategorieId;
    /** Optional: Wenn gesetzt, wird ein vorhandener Stammartikel referenziert statt Freitext zu nutzen. */
    private Long artikelId;
    private String produktname;
    private String produkttext;
    private BigDecimal menge;
    private String einheit;
    /** Fixmaß pro Stück in Millimetern (z. B. 6000) — optional, für auf Länge bestellte Träger. */
    private Integer fixmassMm;
    private String zeugnisAnforderung;
    private String kommentar;
    /** Sonderzuschnitt: Schnittbild + zwei Winkel. NULL = normaler 90°-Zuschnitt. */
    private Long schnittbildId;
    private Double anschnittWinkelLinks;
    private Double anschnittWinkelRechts;
}
