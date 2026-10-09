package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PositionsSuchtextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Flachstahl 50 × 5  S235JR | flachstahl 50x5 s235jr",
            "Blech 2000 X 1000 x 3     | blech 2000x1000x3",
            "Rohr 60,3*2,9             | rohr 60,3x2,9",
            "Mutter M12 x Gewinde      | mutter m12 x gewinde",
            "  IPE   200  |ipe 200" })
    void normalisiert(String eingabe, String erwartet) {
        assertThat(PositionsSuchtext.normalisiere(eingabe)).isEqualTo(erwartet);
    }

    @Test
    void geschuetztesLeerzeichenWieImSqlBackfill() {
        assertThat(PositionsSuchtext.normalisiere("Flachstahl\u00a050\u00a0x\u00a05")).isEqualTo("flachstahl 50x5");
    }

    @Test
    void leerWirdNull() {
        assertThat(PositionsSuchtext.normalisiere(null)).isNull();
        assertThat(PositionsSuchtext.normalisiere("   ")).isNull();
    }

    @Test
    void keinBacktrackingBeiLangenEingaben() {
        String lang = "1" + " ".repeat(50_000) + "y";
        assertThat(PositionsSuchtext.normalisiere(lang)).isEqualTo("1 y");
    }

    @Test
    void bildetSuchtextAusTeilen() {
        assertThat(PositionsSuchtext.bilde("Flachstahl", null, " ", "S235JR+AR", "50 x 5"))
                .isEqualTo("flachstahl s235jr+ar 50x5");
        assertThat(PositionsSuchtext.bilde()).isNull();
        assertThat(PositionsSuchtext.bilde((String[]) null)).isNull();
        assertThat(PositionsSuchtext.bilde("a".repeat(1200))).hasSize(PositionsSuchtext.MAX_LAENGE);
    }

    @Test
    void zerlegtSuchwoerter() {
        assertThat(PositionsSuchtext.suchwoerter("Flachstahl 50 x 5")).containsExactly("flachstahl", "50x5");
        assertThat(PositionsSuchtext.suchwoerter("aa bb cc dd ee ff")).containsExactly("aa", "bb", "cc", "dd", "ee");
        // Ein-Zeichen-Wörter fänden fast alles
        assertThat(PositionsSuchtext.suchwoerter("rohr a 5")).containsExactly("rohr");
        assertThat(PositionsSuchtext.suchwoerter("a b")).isEmpty();
        assertThat(PositionsSuchtext.suchwoerter("rohr ROHR")).containsExactly("rohr");
        assertThat(PositionsSuchtext.suchwoerter("x")).isEmpty();
        assertThat(PositionsSuchtext.suchwoerter("   ")).isEmpty();
        assertThat(PositionsSuchtext.suchwoerter(null)).isEmpty();
        assertThat(PositionsSuchtext.suchwoerter("a".repeat(10_001)))
                .containsExactly("a".repeat(PositionsSuchtext.MAX_EINGABE));
    }

    @Test
    void maskiertLikeSonderzeichen() {
        assertThat(PositionsSuchtext.enthaeltMuster("50%_!")).isEqualTo("%50!%!_!!%");
        assertThat(PositionsSuchtext.enthaeltMuster("'; drop table x; --")).isEqualTo("%'; drop table x; --%");
    }

    @Test
    void normalisiertCharge() {
        assertThat(PositionsSuchtext.charge("12 34-56")).isEqualTo("123456");
        assertThat(PositionsSuchtext.charge("ab-12c")).isEqualTo("AB12C");
        assertThat(PositionsSuchtext.charge("A1")).isNull();
        assertThat(PositionsSuchtext.charge(null)).isNull();
    }

    @Test
    void positionBildetSuchtextBeimSpeichern() {
        LieferantDokumentPosition p = new LieferantDokumentPosition();
        p.setBezeichnung("Flachstahl");
        p.setExterneArtikelnummer("MAT-001");
        p.setWerkstoff("S235JR");
        p.setCharge("123456");
        p.setAbmessung("50 × 5");
        p.baueSuchtext();
        assertThat(p.getSuchtext()).isEqualTo("flachstahl mat-001 s235jr 123456 50x5");
    }
}
