package org.example.kalkulationsprogramm.domain;

/**
 * Qualifikationsstufe eines Mitarbeiters.
 */
public enum Qualifikation {
    AUSZUBILDENDER("Auszubildender"),
    FACHARBEITER("Facharbeiter"),
    MEISTER("Meister");

    private final String bezeichnung;

    Qualifikation(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    /**
     * Konvertiert einen String zur Qualifikation (case-insensitive).
     */
    public static Qualifikation fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (Qualifikation q : values()) {
            if (q.name().equalsIgnoreCase(normalized) || 
                q.bezeichnung.equalsIgnoreCase(value.trim())) {
                return q;
            }
        }
        return null;
    }
}
