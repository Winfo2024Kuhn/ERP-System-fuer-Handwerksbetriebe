package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT) // Wichtig für die API-Antwort
public enum Verrechnungseinheit {
    LAUFENDE_METER("Laufende Meter"),
    QUADRATMETER("Quadratmeter"),
    KILOGRAMM("Kilogramm"),
    STUECK ("Stück");

    private final String anzeigename;

    Verrechnungseinheit(String anzeigename) {
        this.anzeigename = anzeigename;
    }

    // Standard-Getter für den Enum-Namen (z.B. "LAUFENDE_METER")
    public String getName() {
        return this.name();
    }

    @JsonCreator
    public static Verrechnungseinheit fromValue(String value) {
        for (Verrechnungseinheit v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unbekannte Verrechnungseinheit: " + value);
    }
}
