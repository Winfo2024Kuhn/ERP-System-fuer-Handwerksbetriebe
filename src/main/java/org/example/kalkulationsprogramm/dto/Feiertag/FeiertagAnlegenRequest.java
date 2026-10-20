package org.example.kalkulationsprogramm.dto.Feiertag;

import java.time.LocalDate;

/** Eigener Feiertag des Betriebs (z. B. Heiligabend als halber Tag). Prüfung im FeiertagService. */
public record FeiertagAnlegenRequest(LocalDate datum, String bezeichnung, boolean halbTag) {
}
