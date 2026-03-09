package org.example.kalkulationsprogramm.util;

import java.util.Objects;

/**
 * Beschreibt einen feldbezogenen Fehler, den das Frontend direkt anzeigen kann.
 */
public record FieldErrorDetail(String field, String label, String message) {
    /**
     * Stellt sicher, dass entweder ein Feldname oder ein Label gesetzt ist und trimmt alle
     * Eingaben, damit die Darstellung konsistent bleibt.
     */
    public FieldErrorDetail {
        field = field == null ? null : field.trim();
        label = label == null ? null : label.trim();
        message = message == null ? null : message.trim();

        if ((field == null || field.isEmpty()) && (label == null || label.isEmpty())) {
            throw new IllegalArgumentException("Es muss entweder ein Feldname oder ein Label angegeben werden.");
        }
        Objects.requireNonNull(message, "Die Fehlermeldung darf nicht null sein.");
    }
}
