package org.example.kalkulationsprogramm.dto.Beitraege;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO für die Kurzdarstellung eines Beitrags aus der Website-API.
 * Wird für Listenansichten verwendet.
 * Unbekannte Felder werden ignoriert, damit additive Änderungen seitens
 * Website nicht direkt eine ERP-Anpassung erfordern.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeitragSummaryDto {
    private Long id;
    private String slug;
    private String title;
    private String excerpt;
    private String status; // "draft" | "published"
    private String publishedAt;
    private String coverImagePath;
}
