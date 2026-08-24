package org.example.kalkulationsprogramm.dto.Beitraege;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request-DTO zum Erstellen oder Aktualisieren eines Beitrags.
 * Alle Felder sind erforderlich und dürfen nicht leer sein.
 */
@Getter
@Setter
public class BeitragUpsertRequest {
    @NotBlank(message = "Titel darf nicht leer sein.")
    private String title;

    @NotBlank(message = "Zusammenfassung darf nicht leer sein.")
    private String excerpt;

    @NotBlank(message = "Inhalt darf nicht leer sein.")
    private String content;
}
