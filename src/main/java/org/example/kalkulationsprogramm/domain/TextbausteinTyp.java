package org.example.kalkulationsprogramm.domain;

public enum TextbausteinTyp {
    VORTEXT,
    NACHTEXT,
    ZAHLUNGSZIEL,
    FREITEXT;

    public static TextbausteinTyp fromString(String value) {
        if (value == null) {
            return FREITEXT;
        }
        return switch (value.trim().toUpperCase()) {
            case "VORTEXT" -> VORTEXT;
            case "NACHTEXT" -> NACHTEXT;
            case "ZAHLUNGSZIEL" -> ZAHLUNGSZIEL;
            default -> FREITEXT;
        };
    }
}
