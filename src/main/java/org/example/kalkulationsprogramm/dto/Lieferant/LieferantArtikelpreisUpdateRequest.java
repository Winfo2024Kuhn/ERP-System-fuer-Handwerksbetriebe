package org.example.kalkulationsprogramm.dto.Lieferant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LieferantArtikelpreisUpdateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal preis,
        String externeArtikelnummer
) {
}
