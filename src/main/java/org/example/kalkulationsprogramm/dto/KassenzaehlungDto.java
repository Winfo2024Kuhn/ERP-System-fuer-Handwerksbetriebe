package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTOs fuer den Kassensturz -- das Zaehlen des tatsaechlich vorhandenen
 * Bargelds und den Abgleich mit dem Kassenbuch.
 */
public class KassenzaehlungDto {

    private KassenzaehlungDto() {
    }

    /**
     * Was der Buchhalter beim Zaehlen eingibt.
     *
     * <p>Entweder er tippt die Endsumme direkt in {@code gezaehlterBestand},
     * oder er fuellt {@code stueckelung} aus (Schluessel = Wert des Scheins
     * bzw. der Muenze in Euro, Wert = Anzahl) und laesst den Server rechnen.
     * Sind beide da, gewinnt die Stueckelung -- gezaehlt ist gezaehlt.</p>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZaehlRequest {
        private LocalDate stichtag;
        private BigDecimal gezaehlterBestand;
        /** z.B. {"50": 2, "20": 3, "0.50": 7} */
        private Map<String, Integer> stueckelung;
        private String bemerkung;
        /**
         * true = eine festgestellte Differenz wird sofort als Kassenfehlbetrag
         * bzw. Kassenueberschuss gebucht, damit gezaehlter und rechnerischer
         * Bestand danach wieder uebereinstimmen.
         */
        private Boolean differenzBuchen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private LocalDate stichtag;
        private LocalDateTime gezaehltAm;
        private BigDecimal gezaehlterBestand;
        private BigDecimal rechnerischerBestand;
        private BigDecimal differenz;
        private String stueckelungJson;
        private String bemerkung;
        /** Beleg-ID der Ausgleichsbuchung, falls die Differenz gebucht wurde. */
        private Long ausgleichBelegId;
        private String erfasstVon;
    }
}
