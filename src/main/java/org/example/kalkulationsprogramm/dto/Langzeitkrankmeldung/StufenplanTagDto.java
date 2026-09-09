package org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ein einzelner Tag im Stufenplan einer Wiedereingliederung: geplante gegen
 * tatsaechlich gestempelte Stunden. Kein Blocker, nur eine Markierung fuer
 * die Detailseite (siehe Spec, Abschnitt 7).
 */
@Data
public class StufenplanTagDto {
    private LocalDate datum;
    private BigDecimal geplanteStunden;
    private BigDecimal gestempelteStunden;
    private boolean ueberPlan;
}
