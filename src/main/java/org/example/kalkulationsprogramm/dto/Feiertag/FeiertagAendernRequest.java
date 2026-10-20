package org.example.kalkulationsprogramm.dto.Feiertag;

/** Feiertag als halben Tag markieren bzw. abschalten. */
public record FeiertagAendernRequest(boolean halbTag, boolean aktiv) {
}
