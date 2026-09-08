package org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request-Body fuer das Anlegen und Aendern einer
 * {@link org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase}.
 */
@Data
public class LangzeitkrankmeldungPhaseRequest {
    private LangzeitkrankmeldungPhaseTyp typ;
    private LocalDate vonDatum;
    private LocalDate bisDatum;
    /** Nur bei {@code WIEDEREINGLIEDERUNG} gesetzt. */
    private BigDecimal stundenProTag;
}
