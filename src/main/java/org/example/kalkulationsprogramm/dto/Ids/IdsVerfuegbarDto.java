package org.example.kalkulationsprogramm.dto.Ids;

import java.util.List;

/** Ob der Knopf „Aus Lieferanten-Shop“ erscheint und welche Shops dahinter wählbar sind. */
public record IdsVerfuegbarDto(boolean aktiv, List<Lieferant> lieferanten) {

    /** @param passwortGesetzt ohne Passwort lehnt ein Shop die Anmeldung meist ab – der Dialog warnt vorher */
    public record Lieferant(Long lieferantId, String lieferantName, boolean passwortGesetzt) {
    }
}
