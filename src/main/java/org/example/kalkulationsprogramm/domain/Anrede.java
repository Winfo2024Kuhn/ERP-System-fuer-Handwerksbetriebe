package org.example.kalkulationsprogramm.domain;

public enum Anrede {
    HERR,
    FRAU,
    FAMILIE,
    FIRMA,
    DAMEN_HERREN;

    public static Anrede fromString(String value) {
        if (value == null)
            return null;
        try {
            return Anrede.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Konvertiert das Enum in einen korrekten Anredetext für E-Mails.
     * z.B. HERR -> "Sehr geehrter Herr", FRAU -> "Sehr geehrte Frau"
     */
    public String toAnredeText() {
        return switch (this) {
            case HERR -> "Sehr geehrter Herr";
            case FRAU -> "Sehr geehrte Frau";
            case FAMILIE -> "Sehr geehrte Familie";
            case FIRMA -> "Sehr geehrte Damen und Herren";
            case DAMEN_HERREN -> "Sehr geehrte Damen und Herren";
        };
    }
}
