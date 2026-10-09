package org.example.kalkulationsprogramm.dto;

import java.math.BigDecimal;

/** Eine gefundene Position mit ihrem Dokument – nur, was die Trefferzeile braucht. */
public record PositionsSuchtreffer(Long dokumentId, int positionNr, String bezeichnung, String werkstoff,
        String charge, String abmessung, BigDecimal menge, String mengeneinheit) {
}
