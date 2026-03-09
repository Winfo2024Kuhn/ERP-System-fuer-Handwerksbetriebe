package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Data;

/**
 * DTO zum Übertragen einer externen Artikelnummer mit optionalem Lieferanten.
 */
@Data
public class ExterneNummerDto {
    private Long lieferantId;
    private String nummer;
}

