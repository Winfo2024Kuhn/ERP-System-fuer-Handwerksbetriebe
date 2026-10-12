package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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

    private static Eingabe ware(long id, String betrag, Ziel ziel) {
        return new Eingabe(id, PositionsArt.WARE, new BigDecimal(betrag), ziel, Set.of());
    }

    private static Eingabe zuschlag(PositionsArt art, String betrag, Long... bezug) {
        return new Eingabe(99L, art, new BigDecimal(betrag), null, Set.of(bezug));
    }

    @Test
    void zuschlagMitBezugLandetNurBeimProjektSeinerPosition() {
        // Issue #242: 700 Ware A, 300 Ware B, 100 Legierungszuschlag nur zu Pos. B, 50 Fracht für alle
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "700", PROJEKT_A), ware(2L, "300", PROJEKT_B),
                        zuschlag(PositionsArt.NEBENKOSTEN, "100", 2L), nebenkosten("50")),
                new BigDecimal("1150.00"), new BigDecimal("1368.50"));

        var a = ergebnis.ziele().get(0);
        var b = ergebnis.ziele().get(1);
        // A: 700 + 0,7 × 50 = 735; B: 300 + 100 + 0,3 × 50 = 415
        assertThat(a.betragNetto()).isEqualByComparingTo("735.00");
        assertThat(b.betragNetto()).isEqualByComparingTo("415.00");
        assertThat(a.betragBrutto().add(b.betragBrutto())).isEqualByComparingTo("1368.50");
        assertThat(b.betrag()).isEqualByComparingTo("493.85");
        assertThat(a.nebenkostenMitBezug()).isEqualByComparingTo("0");
        assertThat(b.nebenkostenMitBezug()).isEqualByComparingTo("100");
        // Der Anteil am Warenwert bleibt unverändert.
        assertThat(a.anteil()).isEqualByComparingTo("0.7");
        assertThat(ergebnis.nebenkosten()).isEqualByComparingTo("150");
        assertThat(ergebnis.nebenkostenMitBezug()).isEqualByComparingTo("100");
    }

    @Test
    void zuschlagFuerZweiPositionenNachDerenWarenwert() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "100", PROJEKT_A), ware(2L, "300", PROJEKT_B), ware(3L, "600", LAGER),
                        zuschlag(PositionsArt.NEBENKOSTEN, "40", 1L, 2L)),
                new BigDecimal("1040.00"), new BigDecimal("1237.60"));

        assertThat(ergebnis.ziele()).extracting(z -> z.betragNetto().toPlainString())
                .containsExactly("110.00", "330.00", "600.00");
        assertThat(ergebnis.ziele().get(2).betrag()).isEqualByComparingTo("600.00");
    }

    @Test
    void rabattMitBezugMindertNurSeinProjekt() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "500", PROJEKT_A), ware(2L, "500", PROJEKT_B),
                        zuschlag(PositionsArt.RABATT, "-50", 1L)),
                new BigDecimal("950.00"), null);

        assertThat(ergebnis.ziele()).extracting(z -> z.betragNetto().toPlainString())
                .containsExactly("450.00", "500.00");
        assertThat(ergebnis.ziele().get(0).nebenkostenMitBezug()).isEqualByComparingTo("-50");
    }

    @Test
    void bezugAufPositionOhneZielWirdAnteiligVerteilt() {
        // Pos. 3 hat noch kein Projekt: der Zuschlag weiß nicht wohin und zählt wie Fracht.
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "700", PROJEKT_A), ware(2L, "300", PROJEKT_B), ware(3L, "100", null),
                        zuschlag(PositionsArt.NEBENKOSTEN, "100", 3L, 77L)),
                null, null);

        assertThat(ergebnis.nichtZugeordnet()).isEqualTo(1);
        assertThat(ergebnis.nebenkostenMitBezug()).isEqualByComparingTo("0");
        assertThat(ergebnis.ziele()).extracting(z -> z.betragNetto().toPlainString())
                .containsExactly("770.00", "330.00");
    }

    @Test
    void ohneBelegbetragZaehltDerZuschlagBeimEigenenProjekt() {
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "100", PROJEKT_A), ware(2L, "100", PROJEKT_B),
                        zuschlag(PositionsArt.NEBENKOSTEN, "20", 2L)),
                null, null);

        assertThat(ergebnis.abweichung()).isNull();
        assertThat(ergebnis.ziele()).extracting(z -> z.betragNetto().toPlainString())
                .containsExactly("100.00", "120.00");
    }

    @Test
    void belegUeberNullVerteiltNachWarenwert() {
        // Belegnetto 0 (z. B. Nullrechnung): kein Teilen durch null, Anteile nach Ware.
        var ergebnis = PositionsAufteilung.berechne(
                List.of(ware(1L, "100", PROJEKT_A), ware(2L, "100", PROJEKT_B),
                        zuschlag(PositionsArt.RABATT, "-200", 1L)),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(ergebnis.ziele()).extracting(z -> z.betragNetto().signum()).containsOnly(0);
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
