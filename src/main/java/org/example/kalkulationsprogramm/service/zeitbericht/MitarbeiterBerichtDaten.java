package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.service.Urlaubskonto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inhalt eines Mitarbeiter-Berichts (Stundenzettel) für einen Zeitraum.
 *
 * @param stundenkontoStand Plus-/Minusstunden seit Eintritt bis zum Ende des Zeitraums,
 *                          inklusive Korrekturen (gleiche Rechnung wie in der Zeiterfassung)
 * @param urlaub            Urlaubskonto des Jahres, in dem der Zeitraum endet (heutiger Stand)
 */
public record MitarbeiterBerichtDaten(String name, LocalDate von, LocalDate bis, List<Tag> tage, Summen summen,
        BigDecimal stundenkontoStand, Urlaubskonto urlaub) {

    /**
     * Ein Kalendertag.
     *
     * @param soll        Sollstunden laut Vertrag (Feiertag zählt als bezahlter Arbeitstag)
     * @param arbeit      gebuchte Arbeitszeit ohne Pausen
     * @param abwesenheit Urlaub, Krankheit oder Fortbildung (Bezeichnung), sonst {@code null}
     * @param feiertag    Name des Feiertags, sonst {@code null}
     * @param ist         Arbeit + Abwesenheit + Feiertagsgutschrift
     */
    public record Tag(LocalDate datum, BigDecimal soll, BigDecimal arbeit, String abwesenheit,
            BigDecimal abwesenheitStunden, String feiertag, BigDecimal feiertagStunden, BigDecimal ist) {

        public BigDecimal differenz() {
            return ist.subtract(soll);
        }
    }

    public record Summen(BigDecimal soll, BigDecimal arbeit, BigDecimal urlaub, BigDecimal krankheit,
            BigDecimal fortbildung, BigDecimal feiertage, BigDecimal ist) {

        public BigDecimal differenz() {
            return ist.subtract(soll);
        }
    }
}
