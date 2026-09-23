package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Read-only, batchable current inquiry totals grouped by one unique origin per need. */
public interface EinkaufAnfrageMengenProvider {
    Map<Long, BigDecimal> angefragtFuerBedarfe(Collection<Long> bedarfIds);
}
