package org.example.kalkulationsprogramm.dto;

import java.util.List;

/** Persönliche Einrichtung und Historie, ohne implizite Kontoanlage. */
public record ZeitkontoStatusDto(Long mitarbeiterId, Long mitarbeiterVersion, String mitarbeiterName,
        boolean fuehrtZeitkonto, boolean istGeschaeftsfuehrer, boolean eingerichtet, String hinweis,
        ZeitkontoVersionDto aktuell, ZeitkontoVersionDto letzteVersion,
        List<ZeitkontoVersionDto> historie) {
}
