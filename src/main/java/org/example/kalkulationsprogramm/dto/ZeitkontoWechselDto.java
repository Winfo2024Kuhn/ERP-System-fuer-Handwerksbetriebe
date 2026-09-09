package org.example.kalkulationsprogramm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Erwartete letzte Version: beide Felder null nur bei erster Zuweisung.
 * Arbeitszeit null kopiert die Vorlage vollständig; sonst explizite persönliche Werte.
 */
public record ZeitkontoWechselDto(@NotNull LocalDate gueltigVon,
        @NotNull @PositiveOrZero Long expectedMitarbeiterVersion,
        @Positive Long expectedLetzteVersionId, @PositiveOrZero Long expectedLetzteVersion,
        @Positive Long vorlageId, @PositiveOrZero Long expectedVorlageVersion,
        @Valid ZeitkontenmodellDto.Arbeitszeit arbeitszeit) {
    public record Ausschalten(@NotNull @PositiveOrZero Long expectedMitarbeiterVersion,
            @Positive Long expectedLetzteVersionId,
            @PositiveOrZero Long expectedLetzteVersion) {}
}
