package org.example.kalkulationsprogramm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Vorher-/Nachher-Werte für eine bewusste Arbeitszeitänderung. */
public record ZeitkontoWechselErgebnisDto(ZeitkontoStatusDto zeitkonto, LocalDate gueltigVon,
        boolean gespeichert, long bestehendeAbwesenheiten, String hinweis, List<Monat> monate) {
    public record Monat(int jahr, int monat, boolean abgeschlossen, BigDecimal saldoVorher,
            BigDecimal saldoNachher, boolean geaendert) {}
    public record Auswahl(@NotNull @Positive Long mitarbeiterId, @NotNull @Valid ZeitkontoWechselDto wechsel) {}
    public record Mehrere(@NotEmpty @Size(max = 100) List<@NotNull @Valid Auswahl> mitarbeiter) {}
}
