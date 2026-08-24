package org.example.kalkulationsprogramm.dto.Beitraege;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO für ein einzelnes Beitragsbild aus der Website-API.
 * Unbekannte Felder werden ignoriert, damit additive Änderungen seitens
 * Website nicht direkt eine ERP-Anpassung erfordern.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeitragBildDto {
    private Long id;
    private Long postId;
    private String path;
    private String altText;
    private Integer sortOrder;
    private Boolean isCover;
}
