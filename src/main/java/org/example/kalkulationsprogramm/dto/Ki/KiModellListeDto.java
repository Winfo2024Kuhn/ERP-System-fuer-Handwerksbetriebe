package org.example.kalkulationsprogramm.dto.Ki;

import java.util.List;

/**
 * Chat-Modelle aller Anbieter mit Schlüssel, für die Modellsuche in den Einstellungen.
 *
 * @param hinweise warum Modelle eines Anbieters fehlen (kein Schlüssel, nicht erreichbar)
 */
public record KiModellListeDto(List<Modell> modelle, List<String> hinweise) {

    /** @param bilder kann es Bilder lesen? {@code null}, wenn der Anbieter es nicht sagt */
    public record Modell(String anbieter, String anbieterName, String modell, String name, Boolean bilder) {
    }
}
