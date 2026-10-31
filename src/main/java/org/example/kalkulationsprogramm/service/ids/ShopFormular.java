package org.example.kalkulationsprogramm.service.ids;

import java.util.Map;

/**
 * Formular, das der Browser per POST an den Shop schickt. Enthält das Shop-Passwort im Klartext –
 * nie loggen; {@code toString} blendet die Felder deshalb aus.
 */
public record ShopFormular(String action, String enctype, Map<String, String> felder) {

    public ShopFormular {
        // Reihenfolge behalten: manche ältere Shops lesen die Felder der Reihe nach.
        felder = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(felder));
    }

    @Override
    public String toString() {
        return "ShopFormular[action=" + action + ", enctype=" + enctype + ", felder=" + felder.keySet() + "]";
    }
}
