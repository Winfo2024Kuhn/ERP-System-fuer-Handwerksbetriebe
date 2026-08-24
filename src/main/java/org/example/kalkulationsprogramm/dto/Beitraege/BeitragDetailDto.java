package org.example.kalkulationsprogramm.dto.Beitraege;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO für die Detaildarstellung eines Beitrags aus der Website-API.
 * Erweitert BeitragSummaryDto um den vollständigen Text und die zugehörigen Bilder.
 * Unbekannte Felder werden ignoriert, damit additive Änderungen seitens
 * Website nicht direkt eine ERP-Anpassung erfordern.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeitragDetailDto extends BeitragSummaryDto {
    private String content;
    private List<BeitragBildDto> images;
}
