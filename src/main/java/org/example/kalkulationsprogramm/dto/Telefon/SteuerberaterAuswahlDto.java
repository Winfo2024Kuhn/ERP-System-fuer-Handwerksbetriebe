package org.example.kalkulationsprogramm.dto.Telefon;

import java.util.List;

/**
 * Kanzlei zur Auswahl beim Zuordnen eines Anrufs – mit ihren Ansprechpartnern,
 * damit die Nummer gleich bei der richtigen Person gespeichert werden kann.
 */
public record SteuerberaterAuswahlDto(Long id, String name, List<Ansprechpartner> ansprechpartner) {

    /** @param telefon die dort schon hinterlegte Nummer oder null */
    public record Ansprechpartner(Long id, String name, String telefon) {
    }
}
