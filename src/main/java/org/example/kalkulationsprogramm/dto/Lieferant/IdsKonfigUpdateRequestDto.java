package org.example.kalkulationsprogramm.dto.Lieferant;

import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Size;

/**
 * Eingabe-DTO zum Aktualisieren der IDS-Connect-Konfiguration eines
 * Lieferanten. Validation verhindert, dass {@code javascript:} oder
 * {@code file:}-URLs als Punchout-URL gespeichert werden, weil diese
 * später ungeprüft in das Punchout-Auto-Submit-Form gerendert würden.
 *
 * <p>Das Passwort kommt im Klartext (oder als Platzhalter, wenn der
 * Admin den Wert nicht ändern will) und wird im Service AES-GCM-
 * verschlüsselt.</p>
 */
public record IdsKonfigUpdateRequestDto(
        boolean aktiviert,
        IdsProtokoll protokoll,

        @URL(protocol = "https", regexp = "^https://.*")
        @Size(max = 500)
        String punchoutUrl,

        @Size(max = 100)
        String kundennummer,

        @Size(max = 100)
        String loginName,

        @Size(max = 500)
        String passwort,

        @Size(max = 4000)
        String notizen) {
}
