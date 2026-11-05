package org.example.kalkulationsprogramm.service.pdf;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBerichtBausteineTest {

    @Test
    void zahlenWerdenDeutschMitEinerNachkommastelleGeschrieben() {
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("1234.56"))).isEqualTo("1.234,6");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("0"))).isEqualTo("0,0");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("-3.5"))).isEqualTo("-3,5");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("-0.01"))).isEqualTo("0,0");
        assertThat(PdfBerichtBausteine.zahl(null)).isEmpty();
    }
}
