package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UntdidCodelisteTest {

    @Test
    void mapptRechnungsCodesZuRechnung() {
        // 380 Rechnung, 384 korrigiert, 389 Eigenrechnung, 385 Sammelrechnung,
        // 386 Vorauszahlung, 326 Teilrechnung, 875-877 Bau-Abschlagsrechnungen
        assertThat(UntdidCodeliste.typFuer("380")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("384")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("389")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("385")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("386")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("326")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("875")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("876")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(UntdidCodeliste.typFuer("877")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
    }

    @Test
    void mapptTypeCode381ZuGutschrift() {
        assertThat(UntdidCodeliste.typFuer("381")).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
    }

    @Test
    void mapptTypeCode310ZuAngebot() {
        assertThat(UntdidCodeliste.typFuer("310")).isEqualTo(LieferantDokumentTyp.ANGEBOT);
    }

    @Test
    void mapptTypeCode231ZuAuftragsbestaetigung() {
        assertThat(UntdidCodeliste.typFuer("231")).isEqualTo(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
    }

    @Test
    void mapptLieferscheinCodesZuLieferschein() {
        // 351 = Despatch advice (Lieferavis), nicht Angebot - siehe UNTDID 1001.
        assertThat(UntdidCodeliste.typFuer("351")).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
        assertThat(UntdidCodeliste.typFuer("261")).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
        assertThat(UntdidCodeliste.typFuer("270")).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
    }

    @Test
    void gibtNullBeiNullCodeZurueck() {
        assertThat(UntdidCodeliste.typFuer(null)).isNull();
    }

    @Test
    void gibtNullBeiLeerstringZurueck() {
        assertThat(UntdidCodeliste.typFuer("")).isNull();
    }

    @Test
    void gibtNullBeiUnbekanntemCodeZurueck() {
        assertThat(UntdidCodeliste.typFuer("999")).isNull();
    }

    @Test
    void trimtWhitespaceUmDenCode() {
        assertThat(UntdidCodeliste.typFuer(" 380 ")).isEqualTo(LieferantDokumentTyp.RECHNUNG);
    }
}
