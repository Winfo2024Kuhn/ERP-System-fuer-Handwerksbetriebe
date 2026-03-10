package org.example.kalkulationsprogramm.util;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Bündelt alle Informationen, die der Client über einen verletzten Datenbank-Constraint
 * benötigt. Neben einer HTTP-Antwort wird sowohl eine nutzerfreundliche Meldung als auch
 * die technische Ursache mitgeliefert, optional ergänzt um Feldfehler.
 */
public record ConstraintErrorDetail(
        HttpStatus status,
        String userMessage,
        String technicalMessage,
        String constraintName,
        List<FieldErrorDetail> fieldErrors
) {

    /**
     * Validiert die Pflichtfelder und friert die übergebenen Feldfehler als unveränderliche
     * Liste ein, damit Aufrufer keine seitlichen Effekte verursachen können.
     */
    public ConstraintErrorDetail {
        if (status == null) {
            throw new IllegalArgumentException("Der Status darf nicht null sein.");
        }
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
