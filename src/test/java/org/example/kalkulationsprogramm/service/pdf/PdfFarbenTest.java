package org.example.kalkulationsprogramm.service.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class PdfFarbenTest {

    @Test
    void sechsstelligeFirmenfarbeWirdUebernommen() {
        assertThat(PdfFarben.akzent("#500010")).isEqualTo(new Color(0x50, 0x00, 0x10));
        assertThat(PdfFarben.akzent(" #a1B2c3 ")).isEqualTo(new Color(0xA1, 0xB2, 0xC3));
    }

    @Test
    void dreistelligeFirmenfarbeWirdVerdoppelt() {
        assertThat(PdfFarben.akzent("#f0a")).isEqualTo(new Color(0xFF, 0x00, 0xAA));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "500010", "#50001", "#gggggg", "rot", "#5000100", "<script>"})
    void ohneGueltigeFirmenfarbeBleibtDasBisherigeRot(String eingabe) {
        assertThat(PdfFarben.akzent(eingabe)).isEqualTo(PdfFarben.STANDARD_AKZENT);
        assertThat(PdfFarben.STANDARD_AKZENT).isEqualTo(new Color(220, 38, 38));
    }

    @Test
    void helleFarbeIstFastWeissUndBehaeltDenFarbton() {
        Color hell = PdfFarben.hell(new Color(0x50, 0x00, 0x10));
        assertThat(hell.getRed()).isBetween(240, 255);
        assertThat(hell.getGreen()).isBetween(230, 255);
        assertThat(hell.getRed()).isGreaterThan(hell.getGreen());
        assertThat(PdfFarben.hell(Color.WHITE)).isEqualTo(Color.WHITE);
    }
}
