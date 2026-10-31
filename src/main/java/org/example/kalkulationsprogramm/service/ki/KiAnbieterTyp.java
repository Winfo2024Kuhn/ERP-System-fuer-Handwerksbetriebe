package org.example.kalkulationsprogramm.service.ki;

import java.util.Locale;
import java.util.Optional;

/**
 * Die KI-Anbieter, die das Programm anbinden kann. Jeder Wert hat genau eine
 * {@link KiAnbieter}-Umsetzung. Die Reihenfolge ist auch die Reihenfolge, in
 * der ein Ersatz-Anbieter gesucht wird.
 */
public enum KiAnbieterTyp {
    MISTRAL,
    GEMINI;

    /** Kurzname für Einstellungs-Schlüssel, z. B. {@code ai.mistral.api-key}. */
    public String kuerzel() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Liest einen Wert aus Einstellung oder Anfrage, ohne bei Unbekanntem zu werfen. */
    public static Optional<KiAnbieterTyp> lies(String wert) {
        if (wert == null || wert.isBlank()) {
            return Optional.empty();
        }
        String gesucht = wert.trim().toUpperCase(Locale.ROOT);
        for (KiAnbieterTyp typ : values()) {
            if (typ.name().equals(gesucht)) {
                return Optional.of(typ);
            }
        }
        return Optional.empty();
    }
}
