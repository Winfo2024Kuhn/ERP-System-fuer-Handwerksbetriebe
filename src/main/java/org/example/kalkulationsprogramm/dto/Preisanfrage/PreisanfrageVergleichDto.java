package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vergleichs-Matrix fuer eine Preisanfrage: Positionen in den Zeilen,
 * Lieferanten in den Spalten, pro Zelle ein Angebotspreis (oder {@code null}).
 * Pro Zeile ist der guenstigste Preis markiert.
 */
@Getter
@Setter
@NoArgsConstructor
public class PreisanfrageVergleichDto {

    private Long preisanfrageId;
    private String nummer;
    private String bauvorhaben;
    private List<LieferantSpalte> lieferanten = new ArrayList<>();
    private List<PositionZeile> positionen = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LieferantSpalte {
        private Long preisanfrageLieferantId;
        private Long lieferantId;
        private String lieferantenname;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PositionZeile {
        private Long preisanfragePositionId;
        private String produktname;
        private BigDecimal menge;
        private String einheit;
        private List<AngebotsZelle> zellen = new ArrayList<>();
        /** ID des Lieferanten mit dem guenstigsten Einzelpreis in dieser Zeile, oder {@code null}. */
        private Long guenstigsterPreisanfrageLieferantId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AngebotsZelle {
        private Long preisanfrageLieferantId;
        private BigDecimal einzelpreis;
        private BigDecimal gesamtpreis;
        private BigDecimal mwstProzent;
        private Integer lieferzeitTage;
        private String bemerkung;
        private boolean guenstigster;
    }
}
