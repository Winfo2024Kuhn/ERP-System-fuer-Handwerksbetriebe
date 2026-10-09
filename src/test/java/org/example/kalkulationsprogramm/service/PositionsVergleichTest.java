package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.junit.jupiter.api.Test;

class PositionsVergleichTest {

    private static AusgelesenePosition ware(String nummer, String text, String menge) {
        return new AusgelesenePosition(PositionsArt.WARE, nummer, text,
                menge != null ? new BigDecimal(menge) : null, "Stück", null, null, null);
    }

    private static PositionsVergleich.Ergebnis vergleiche(List<AusgelesenePosition> a, List<AusgelesenePosition> b) {
        return PositionsVergleich.vergleiche(PositionsVergleich.merkmale(a), PositionsVergleich.merkmale(b));
    }

    @Test
    void gleicheArtikelnummernTrotzSchreibweise() {
        var ergebnis = vergleiche(
                List.of(ware("100-200", null, "5"), ware("100-300", null, "2")),
                List.of(ware("100200", null, "5"), ware("100 300", null, "2")));

        assertThat(ergebnis.gleich()).isEqualTo(2);
        assertThat(ergebnis.passt()).isTrue();
        assertThat(ergebnis.mengenPassen()).isTrue();
    }

    @Test
    void bezeichnungOhneNummerZaehlt() {
        var angebot = List.of(
                ware(null, "Flachstahl 50x5 S235JR, 6 m", "10"),
                ware(null, "Rundrohr 42,4x2 verzinkt", "4"),
                ware(null, "Handlaufhalter Edelstahl V2A", "12"));
        var ab = List.of(
                ware(null, "FLACHSTAHL 50X5 S235JR 6m", "10"),
                ware(null, "Rundrohr 42,4 x 2 verzinkt", "4"),
                ware(null, "Handlaufhalter Edelstahl V2A", "12"));

        var ergebnis = vergleiche(angebot, ab);

        assertThat(ergebnis.gleich()).isEqualTo(3);
        assertThat(ergebnis.stark()).isTrue();
        assertThat(ergebnis.mengenPassen()).isTrue();
    }

    @Test
    void weggefalleneUndNeuePositionenSchadenNicht() {
        // Angebot mit 4 Positionen, AB: eine fällt weg, eine kommt dazu
        var angebot = List.of(
                ware("ART-101", "Geländerpfosten", "8"), ware("ART-102", "Glasklemme", "16"),
                ware("ART-103", "Handlauf", "2"), ware("ART-104", "Wandanschluss", "2"));
        var ab = List.of(
                ware("ART-101", "Geländerpfosten", "8"), ware("ART-102", "Glasklemme", "16"),
                ware("ART-103", "Handlauf", "2"), ware("ART-109", "Endkappe", "4"));

        var ergebnis = vergleiche(angebot, ab);

        assertThat(ergebnis.gleich()).isEqualTo(3);
        assertThat(ergebnis.anteil()).isEqualTo(0.75);
        assertThat(ergebnis.passt()).isTrue();
        assertThat(ergebnis.stark()).isFalse();
    }

    @Test
    void teillieferungIstTeilmenge() {
        var ab = List.of(ware("ART-101", null, "8"), ware("ART-102", null, "16"), ware("ART-103", null, "2"),
                ware("ART-104", null, "2"), ware("ART-105", null, "1"));
        var lieferschein = List.of(ware("ART-101", null, "8"), ware("ART-102", null, "16"));

        var ergebnis = vergleiche(ab, lieferschein);

        assertThat(ergebnis.kleinere()).isEqualTo(2);
        assertThat(ergebnis.anteil()).isEqualTo(1.0);
        assertThat(ergebnis.passt()).isTrue();
    }

    @Test
    void nebenkostenZaehlenNicht() {
        var fracht = new AusgelesenePosition(PositionsArt.NEBENKOSTEN, null, "Frachtkosten Spedition", null,
                null, null, null, new BigDecimal("45.00"));
        var a = List.of(fracht, ware("ART-101", null, "1"));
        var b = List.of(fracht, ware("BRT-207", null, "1"));

        assertThat(vergleiche(a, b).passt()).isFalse();
    }

    @Test
    void verschiedeneArtikelnummernTrotzGleichemText() {
        var a = List.of(ware("4711", "Schraube M8x40 verzinkt", "100"));
        var b = List.of(ware("4712", "Schraube M8x40 verzinkt", "100"));

        assertThat(vergleiche(a, b).gleich()).isZero();
    }

    @Test
    void wenigUebereinstimmungReichtNicht() {
        var a = List.of(ware("ART-101", null, null), ware("ART-102", null, null), ware("ART-103", null, null));
        var b = List.of(ware("ART-101", null, null), ware("BRT-202", null, null), ware("BRT-203", null, null));

        assertThat(vergleiche(a, b).passt()).isFalse();
    }

    @Test
    void ohneAussagekraeftigenTextKeinMerkmal() {
        assertThat(PositionsVergleich.merkmale(List.of(ware(null, "Diverses", null)))).isEmpty();
        assertThat(PositionsVergleich.merkmale(null)).isEmpty();
    }

    @Test
    void woerterNormalisiert() {
        assertThat(PositionsVergleich.woerter("Stahlträger HEA-200, für Außentreppe"))
                .containsExactlyInAnyOrder("stahltraeger", "hea", "200", "aussentreppe");
    }

    @Test
    void leereListen() {
        assertThat(PositionsVergleich.vergleiche(List.of(), List.of()).passt()).isFalse();
        assertThat(PositionsVergleich.vergleiche(null, null).gleich()).isZero();
    }
}
