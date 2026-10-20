package org.example.kalkulationsprogramm.dto.Feiertag;

import org.example.kalkulationsprogramm.domain.Feiertag;

import java.time.LocalDate;

/** Feiertag für die Feiertage-Seite (Issue #222). */
public record FeiertagDto(Long id, LocalDate datum, String bezeichnung, String bundesland,
        boolean halbTag, boolean aktiv, boolean eigener) {

    public static FeiertagDto von(Feiertag f) {
        return new FeiertagDto(f.getId(), f.getDatum(), f.getBezeichnung(), f.getBundesland(),
                f.isHalbTag(), f.isAktiv(), f.isEigener());
    }
}
