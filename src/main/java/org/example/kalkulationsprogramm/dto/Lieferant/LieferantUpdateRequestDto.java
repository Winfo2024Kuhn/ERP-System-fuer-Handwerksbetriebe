package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

/**
 * Request-DTO für das Aktualisieren bestehender Lieferanten.
 * Erbt alle Validierungen vom Create-DTO, damit Pflichtfelder konsistent bleiben.
 */
@Getter
@Setter
public class LieferantUpdateRequestDto extends LieferantCreateRequestDto {
}
