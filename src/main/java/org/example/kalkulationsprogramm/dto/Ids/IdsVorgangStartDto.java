package org.example.kalkulationsprogramm.dto.Ids;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Shop-Besuch bei diesem Lieferanten starten. */
public record IdsVorgangStartDto(@NotNull @Positive Long lieferantId) {
}
