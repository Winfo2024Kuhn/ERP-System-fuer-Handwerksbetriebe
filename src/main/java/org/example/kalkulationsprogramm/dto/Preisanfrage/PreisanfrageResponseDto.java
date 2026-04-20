package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vollstaendige Ausgabe-Sicht einer Preisanfrage inkl. Lieferanten-Status und
 * Positionen. Wird vom {@code PreisanfrageController} zurueckgegeben; nie
 * Entities direkt ausliefern (Regel aus CLAUDE.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class PreisanfrageResponseDto {

    private Long id;
    private String nummer;
    private String bauvorhaben;
    private Long projektId;
    private LocalDateTime erstelltAm;
    private LocalDate antwortFrist;
    private String status;
    private String notiz;
    private Long vergebenAnPreisanfrageLieferantId;

    private List<PreisanfrageLieferantDto> lieferanten = new ArrayList<>();
    private List<PreisanfragePositionDto> positionen = new ArrayList<>();
}
