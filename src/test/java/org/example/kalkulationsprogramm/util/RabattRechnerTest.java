package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Nagelt die eine verbindliche Rundungsregel fuer den Dokument-Pauschalrabatt fest.
 *
 * <p>Hintergrund: Der Rabatt wurde an drei Stellen unabhaengig gerechnet. Zwei
 * benutzten {@code round2(netto × round4(1 − r/100))}, eine
 * {@code netto − round2(netto × r/100)}. Der Unterschied betraegt einen Cent und
 * ist keineswegs selten — bei 5 % trifft er rund 5 % aller Cent-Betraege. Folge:
 * Der Korrekturlauf haelt ein bereits korrektes, festgeschriebenes Dokument fuer
 * falsch, schreibt es um und hinterlaesst einen GEAENDERT-Audit-Eintrag; im PDF
 * erscheint wieder ein "Verbleibender Restbetrag 0,01 €".</p>
 *
 * <p>DSGVO: ausschliesslich Dummy-Daten.</p>
 */
class RabattRechnerTest {

    @Nested
    @DisplayName("Abzugs-Variante ist verbindlich")
    class Rundung {

        @Test
        @DisplayName("Der Regressionsfall: 100,10 € minus 5 % ergibt 95,09 — nicht 95,10")
        void regressionsfall_einCentUnterschied() {
            BigDecimal netto = new BigDecimal("100.10");

            // round2(100,10 × 5 / 100) = round2(5,005) = 5,01
            assertThat(RabattRechner.rabattBetrag(netto, new BigDecimal("5")))
                    .isEqualByComparingTo("5.01");
            assertThat(RabattRechner.nettoNachRabatt(netto, new BigDecimal("5")))
                    .isEqualByComparingTo("95.09");

            // Die frueher an zwei Stellen verwendete Faktor-Variante haette 95,10 geliefert.
            BigDecimal faktorVariante = netto
                    .multiply(BigDecimal.ONE.subtract(new BigDecimal("5").divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            assertThat(faktorVariante).isEqualByComparingTo("95.10");
            assertThat(RabattRechner.nettoNachRabatt(netto, new BigDecimal("5")))
                    .isNotEqualByComparingTo(faktorVariante);
        }

        @Test
        void realfall_ausDemBugreport() {
            // 3412,20 minus 3 % = 3309,83 netto (× 1,19 = 3938,70 brutto)
            assertThat(RabattRechner.nettoNachRabatt(new BigDecimal("3412.20"), new BigDecimal("3")))
                    .isEqualByComparingTo("3309.83");
        }

        @Test
        void trifft_saetze_mit_nachkommastellen() {
            // Saetze wie 21,4 % lassen sich im Double nicht exakt multiplizieren.
            // Das Frontend bildet sie deshalb ganzzahlig nach (helpers.ts#rabattBetrag).
            assertThat(RabattRechner.rabattBetrag(new BigDecimal("27382.50"), new BigDecimal("21.4")))
                    .isEqualByComparingTo("5859.86");
            assertThat(RabattRechner.rabattBetrag(new BigDecimal("1000.00"), new BigDecimal("16.4")))
                    .isEqualByComparingTo("164.00");
            assertThat(RabattRechner.rabattBetrag(new BigDecimal("12345.67"), new BigDecimal("19.9")))
                    .isEqualByComparingTo("2456.79");
        }

        @Test
        void ohneRabattBleibtDerBetragUnveraendert() {
            BigDecimal netto = new BigDecimal("1000.00");
            assertThat(RabattRechner.nettoNachRabatt(netto, null)).isEqualByComparingTo("1000.00");
            assertThat(RabattRechner.nettoNachRabatt(netto, BigDecimal.ZERO)).isEqualByComparingTo("1000.00");
            assertThat(RabattRechner.nettoNachRabatt(netto, new BigDecimal("-5"))).isEqualByComparingTo("1000.00");
        }

        @Test
        void hundertProzentErgibtNull() {
            assertThat(RabattRechner.nettoNachRabatt(new BigDecimal("1000.00"), new BigDecimal("100")))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void unplausibleWerteWerdenBei100Gekappt() {
            assertThat(RabattRechner.nettoNachRabatt(new BigDecimal("500.00"), new BigDecimal("150")))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void nullNettoBleibtNull() {
            assertThat(RabattRechner.nettoNachRabatt(null, new BigDecimal("5"))).isNull();
            assertThat(RabattRechner.rabattBetrag(null, new BigDecimal("5"))).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    class NormalisiereProzent {

        @Test
        void liefertNullWennKeinWirksamerRabattVorliegt() {
            assertThat(RabattRechner.normalisiereProzent(null)).isNull();
            assertThat(RabattRechner.normalisiereProzent(BigDecimal.ZERO)).isNull();
            assertThat(RabattRechner.normalisiereProzent(new BigDecimal("-1"))).isNull();
        }

        @Test
        void kapptBei100() {
            assertThat(RabattRechner.normalisiereProzent(new BigDecimal("150"))).isEqualByComparingTo("100");
            assertThat(RabattRechner.normalisiereProzent(new BigDecimal("7.5"))).isEqualByComparingTo("7.5");
        }
    }
}
