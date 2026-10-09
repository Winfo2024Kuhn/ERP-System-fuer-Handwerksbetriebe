package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.service.PositionsAufteilung.Eingabe;
import org.example.kalkulationsprogramm.service.PositionsAufteilung.Ziel;
import org.junit.jupiter.api.Test;

class PositionsAufteilungTest {

    private static final Ziel PROJEKT_A = new Ziel(1L, null);
    private static final Ziel PROJEKT_B = new Ziel(2L, null);
    private static final Ziel LAGER = new Ziel(null, 9L);

    private static Eingabe ware(String betrag, Ziel ziel) {
        return new Eingabe(PositionsArt.WARE, new BigDecimal(betrag), ziel);
    }

    private static Eingabe nebenkosten(String betrag) {
        return new Eingabe(PositionsArt.NEBENKOSTEN, new BigDecimal(betrag), null);
    }

    @Test
    void frachtWirdNachWarenwertVerteilt() {
        // 700 + 300 Ware, 100 Fracht = 1100 netto, 1309 brutto
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("700", PROJEKT_A), ware("300", PROJEKT_B), nebenkosten("100")),
                new BigDecimal("1100.00"), new BigDecimal("1309.00"));

        assertThat(ergebnis.nichtZugeordnet()).isZero();
        assertThat(ergebnis.nebenkosten()).isEqualByComparingTo("100");
        var a = ergebnis.ziele().get(0);
        var b = ergebnis.ziele().get(1);
        assertThat(a.betragNetto()).isEqualByComparingTo("770.00");
        assertThat(b.betragNetto()).isEqualByComparingTo("330.00");
        // Projekte werden brutto verbucht
        assertThat(a.betrag()).isEqualByComparingTo("916.30");
        assertThat(b.betrag()).isEqualByComparingTo("392.70");
        assertThat(a.anteil()).isEqualByComparingTo("0.7");
    }

    @Test
    void kostenstelleWirdNettoVerbucht() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("50", PROJEKT_A), ware("50", LAGER)),
                new BigDecimal("100.00"), new BigDecimal("119.00"));

        assertThat(ergebnis.ziele().get(0).betrag()).isEqualByComparingTo("59.50");
        assertThat(ergebnis.ziele().get(1).betrag()).isEqualByComparingTo("50.00");
    }

    @Test
    void rundungsrestGehtAnGroesstesZiel() {
        // Drittelung: 100 / 3 – Summe muss exakt 100,00 ergeben
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("10", PROJEKT_A), ware("10", PROJEKT_B), ware("10.01", LAGER)),
                new BigDecimal("100.00"), new BigDecimal("100.00"));

        BigDecimal summe = ergebnis.ziele().stream().map(PositionsAufteilung.ZielBetrag::betragNetto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(summe).isEqualByComparingTo("100.00");
    }

    @Test
    void mehrerePositionenJeZielWerdenSummiert() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("10", PROJEKT_A), ware("30", PROJEKT_A), ware("60", PROJEKT_B)),
                new BigDecimal("100"), new BigDecimal("119"));

        assertThat(ergebnis.ziele()).hasSize(2);
        assertThat(ergebnis.ziele().get(0).warenwert()).isEqualByComparingTo("40");
    }

    @Test
    void abweichungZumBelegWirdErmitteltUndVerteilt() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("500", PROJEKT_A), ware("500", PROJEKT_B)),
                new BigDecimal("1020.00"), null);

        assertThat(ergebnis.abweichung()).isEqualByComparingTo("20.00");
        assertThat(ergebnis.ziele().get(0).betrag()).isEqualByComparingTo("510.00");
    }

    @Test
    void ohneBelegbetragZaehltPositionssumme() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("80", PROJEKT_A), nebenkosten("20")), null, null);

        assertThat(ergebnis.abweichung()).isNull();
        assertThat(ergebnis.ziele().get(0).betrag()).isEqualByComparingTo("100.00");
    }

    @Test
    void offenePositionenWerdenGezaehlt() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware("80", PROJEKT_A), ware("20", null)), new BigDecimal("100"), new BigDecimal("119"));

        assertThat(ergebnis.nichtZugeordnet()).isEqualTo(1);
    }

    @Test
    void ohnePreiseKeineBetraege() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(new Eingabe(PositionsArt.WARE, null, PROJEKT_A)), new BigDecimal("100"), null);

        assertThat(ergebnis.warenwert()).isEqualByComparingTo("0");
        assertThat(ergebnis.ziele().get(0).betrag()).isNull();
    }

    @Test
    void zielGueltigNurMitGenauEinemZiel() {
        assertThat(new Ziel(1L, null).gueltig()).isTrue();
        assertThat(new Ziel(1L, 2L).gueltig()).isFalse();
        assertThat(new Ziel(null, null).gueltig()).isFalse();
    }
}
