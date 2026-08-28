package org.example.kalkulationsprogramm.dto.Beitraege;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Eingabe fuer den KI-Beitragsvorschlag.
 *
 * @param projektId       Projekt, aus dem Leistungen und Bautagebuch stammen
 * @param verlauf         bisheriger Chat; leer beim ersten Vorschlag
 * @param aktuellerTitel  was gerade im Editor steht, damit Handarbeit nicht verlorengeht
 * @param aktuellerText   dito, als Klartext
 */
public record BeitragKiAnfrage(
        @NotNull @Positive Long projektId,
        List<ChatNachricht> verlauf,
        String aktuellerTitel,
        String aktuellerText) {

    /** rolle ist "user" oder "model". */
    public record ChatNachricht(String rolle, String text) {}

    public List<ChatNachricht> verlaufOderLeer() {
        return verlauf == null ? List.of() : verlauf;
    }
}
