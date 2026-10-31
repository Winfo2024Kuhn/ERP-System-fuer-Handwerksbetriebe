package org.example.kalkulationsprogramm.dto.Ids;

import java.util.Map;

/**
 * Antwort auf den Start eines Shop-Besuchs: das Formular, das der Browser an den Shop schickt.
 * Enthält das Shop-Passwort im Klartext; {@code toString} blendet die Felder aus.
 */
public record IdsVorgangGestartetDto(Long vorgangId, Formular formular) {

    public record Formular(String action, String enctype, Map<String, String> felder) {

        @Override
        public String toString() {
            return "Formular[action=" + action + ", enctype=" + enctype + "]";
        }
    }
}
