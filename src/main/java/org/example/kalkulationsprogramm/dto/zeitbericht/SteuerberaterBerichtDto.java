package org.example.kalkulationsprogramm.dto.zeitbericht;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stundenaufstellung aller Mitarbeiter eines Monats für den Steuerberater (Report Centre).
 * Alle Stundenwerte mit einer Nachkommastelle, Resturlaub in Tagen.
 */
public record SteuerberaterBerichtDto(int jahr, int monat, Stundenart stundenart, List<Zeile> zeilen, Summe summe,
        boolean alleFestgeschrieben) {

    /**
     * Welche Arbeitsstunden an den Steuerberater gehen: {@code SOLL} = bezahlt nach Vertrag
     * (Soll minus Urlaub, Feiertage, Krankheit, Fortbildung), {@code IST} = tatsächlich gebuchte
     * Arbeitszeit ohne Pausen – wie die Stundenart im DATEV-Export.
     */
    public enum Stundenart {
        SOLL,
        IST
    }

    /**
     * @param arbeitsstunden je nach {@link Stundenart}: Soll minus Abwesenheiten oder gebuchte Arbeitszeit
     * @param stundenkontoStand Plus-/Minusstunden seit Eintritt bis Monatsende
     * @param resturlaubTage noch freie Urlaubstage zum heutigen Stand
     */
    public record Zeile(Long mitarbeiterId, String name, BigDecimal tagessoll, BigDecimal sollStunden,
            BigDecimal arbeitsstunden, BigDecimal urlaubStunden, BigDecimal feiertagStunden,
            BigDecimal krankheitStunden, BigDecimal fortbildungStunden, BigDecimal stundenkontoStand,
            BigDecimal resturlaubTage, boolean festgeschrieben) {
    }

    public record Summe(BigDecimal sollStunden, BigDecimal arbeitsstunden, BigDecimal urlaubStunden,
            BigDecimal feiertagStunden, BigDecimal krankheitStunden, BigDecimal fortbildungStunden) {
    }
}
