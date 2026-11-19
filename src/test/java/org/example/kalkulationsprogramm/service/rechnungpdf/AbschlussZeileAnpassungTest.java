package org.example.kalkulationsprogramm.service.rechnungpdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Vom Handwerker angepasste Zeile im Abrechnungsstand: Grenzen und Rueckfall auf den Automatik-Text. */
class AbschlussZeileAnpassungTest {

    @Test
    void ohneAnpassungGiltDerAutomatischeText() {
        assertThat(AbschlussZeileAnpassung.bezeichnungOder(null, "diese Rechnung")).isEqualTo("diese Rechnung");
        assertThat(AbschlussZeileAnpassung.unterzeileOder(null, "ca. 30 %")).isEqualTo("ca. 30 %");
        assertThat(AbschlussZeileAnpassung.ausgeblendet(null)).isFalse();
    }

    @Test
    void leereFelderFallenAufDenAutomatischenTextZurueck() {
        AbschlussZeileAnpassung a = new AbschlussZeileAnpassung("   ", null, null);

        assertThat(AbschlussZeileAnpassung.bezeichnungOder(a, "diese Rechnung")).isEqualTo("diese Rechnung");
        assertThat(AbschlussZeileAnpassung.unterzeileOder(a, null)).isNull();
        assertThat(AbschlussZeileAnpassung.ausgeblendet(a)).isFalse();
    }

    @Test
    void eigeneTexteErsetzenDenAutomatischenText() {
        AbschlussZeileAnpassung a = new AbschlussZeileAnpassung(" Rechnung Treppengeländer ", "Fertigstellung 11/2026", false);

        assertThat(AbschlussZeileAnpassung.bezeichnungOder(a, "diese Rechnung")).isEqualTo("Rechnung Treppengeländer");
        assertThat(AbschlussZeileAnpassung.unterzeileOder(a, "ca. 30 %")).isEqualTo("Fertigstellung 11/2026");
    }

    @Test
    void ausblendenNurBeiAusdruecklichemJa() {
        assertThat(AbschlussZeileAnpassung.ausgeblendet(new AbschlussZeileAnpassung(null, null, true))).isTrue();
        assertThat(AbschlussZeileAnpassung.ausgeblendet(new AbschlussZeileAnpassung(null, null, false))).isFalse();
    }

    @Test
    void grenzenWerdenBeimAnlegenDurchgesetzt() {
        AbschlussZeileAnpassung a = new AbschlussZeileAnpassung("B".repeat(500), "U\n".repeat(500), null);

        assertThat(a.bezeichnung()).hasSize(AbschlussZeileAnpassung.BEZEICHNUNG_MAX_LAENGE).endsWith("…");
        assertThat(a.unterzeile()).hasSize(AbschlussZeileAnpassung.UNTERZEILE_MAX_LAENGE).endsWith("…")
                .doesNotContain("\n");
        assertThat(AbschlussZeileAnpassung.BEZEICHNUNG_MAX_LAENGE).isEqualTo(80);
        assertThat(AbschlussZeileAnpassung.UNTERZEILE_MAX_LAENGE).isEqualTo(200);
    }
}
