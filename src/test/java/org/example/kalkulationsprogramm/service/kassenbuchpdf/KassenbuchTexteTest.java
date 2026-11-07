package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KassenbuchTexteTest {

    @Test
    void betraegeUndKuerzungen() {
        assertThat(KassenbuchTexte.formatEuro(new BigDecimal("1234.5"))).isEqualTo("1.234,50");
        assertThat(KassenbuchTexte.formatEuro(null)).isEqualTo("0,00");
        assertThat(KassenbuchTexte.formatProzent(new BigDecimal("19.00"))).isEqualTo("19 %");
        assertThat(KassenbuchTexte.formatProzent(new BigDecimal("7.5"))).isEqualTo("7,5 %");
        assertThat(KassenbuchTexte.kuerze(null, 5)).isEqualTo("–");
        assertThat(KassenbuchTexte.kuerze(" Werkzeug ", 5)).isEqualTo("Werk…");
        assertThat(KassenbuchTexte.kuerze("Tür", 5)).isEqualTo("Tür");
        assertThat(KassenbuchTexte.kurz("0123456789abcdef0123")).isEqualTo("01234567…cdef0123");
        assertThat(KassenbuchTexte.kurz("kurz")).isEqualTo("kurz");
        assertThat(KassenbuchTexte.joinNonEmpty(" · ", "a", null, " ", "b ")).isEqualTo("a · b");
    }

    @Test
    void mwstAusNettoOderAusSatz() {
        Beleg b = new Beleg();
        assertThat(KassenbuchTexte.mwstBetrag(b)).isNull();
        b.setBetragBrutto(new BigDecimal("119.00"));
        assertThat(KassenbuchTexte.mwstBetrag(b)).isNull();
        b.setMwstSatz(new BigDecimal("19"));
        assertThat(KassenbuchTexte.mwstBetrag(b)).isEqualByComparingTo("19.00");
        b.setBetragNetto(new BigDecimal("100.50"));
        assertThat(KassenbuchTexte.mwstBetrag(b)).isEqualByComparingTo("18.50");
    }

    @Test
    void verwendungszweckGegenkontoUndKategorie() {
        Beleg b = new Beleg();
        b.setBelegKategorie(BelegKategorie.PRIVATENTNAHME);
        assertThat(KassenbuchTexte.verwendungszweck(b)).isEqualTo("Privatentnahme");
        b.setBeschreibung(" Kaffee ");
        assertThat(KassenbuchTexte.verwendungszweck(b)).isEqualTo("Kaffee");
        assertThat(KassenbuchTexte.gegenkonto(b)).isEqualTo("noch offen");
        assertThat(KassenbuchTexte.kategorieLabel(null)).isEqualTo("–");
        for (BelegKategorie k : BelegKategorie.values()) {
            assertThat(KassenbuchTexte.kategorieLabel(k)).isNotBlank();
        }
    }
}
