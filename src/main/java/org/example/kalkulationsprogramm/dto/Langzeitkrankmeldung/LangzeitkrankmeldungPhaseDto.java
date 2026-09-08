package org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ansicht einer einzelnen {@link org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase}
 * fuer die Oberflaeche.
 */
@Data
public class LangzeitkrankmeldungPhaseDto {
    private Long id;
    private LangzeitkrankmeldungPhaseTyp typ;
    private String label;
    private LocalDate vonDatum;
    private LocalDate bisDatum;
    private BigDecimal stundenProTag;
}
