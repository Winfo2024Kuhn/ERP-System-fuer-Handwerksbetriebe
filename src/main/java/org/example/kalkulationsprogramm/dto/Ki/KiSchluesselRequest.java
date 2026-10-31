package org.example.kalkulationsprogramm.dto.Ki;

/** Schlüssel eines KI-Anbieters; {@code toString} verrät ihn nicht. */
public record KiSchluesselRequest(String apiKey) {

    @Override
    public String toString() {
        return "KiSchluesselRequest[apiKey=***]";
    }
}
