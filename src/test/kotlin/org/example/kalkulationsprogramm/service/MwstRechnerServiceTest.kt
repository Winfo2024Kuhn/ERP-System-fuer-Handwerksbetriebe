package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MwstRechnerServiceTest {

    private val rechner = MwstRechnerService()

    @Test
    fun nettoUndSatzLiefertBrutto() {
        val e = rechner.berechne(BigDecimal("100.00"), null, BigDecimal("19"))

        assertThat(e.netto).isEqualByComparingTo("100.00")
        assertThat(e.mwstBetrag).isEqualByComparingTo("19.00")
        assertThat(e.brutto).isEqualByComparingTo("119.00")
    }

    @Test
    fun bruttoUndSatzLiefertNetto() {
        val e = rechner.berechne(null, BigDecimal("119.00"), BigDecimal("19"))

        assertThat(e.netto).isEqualByComparingTo("100.00")
        assertThat(e.brutto).isEqualByComparingTo("119.00")
        assertThat(e.mwstBetrag).isEqualByComparingTo("19.00")
    }

    @Test
    fun nettoUndBruttoLeitetSatzAb() {
        val e = rechner.berechne(BigDecimal("100"), BigDecimal("107"), null)

        assertThat(e.satzProzent).isEqualByComparingTo("7.00")
        assertThat(e.mwstBetrag).isEqualByComparingTo("7.00")
    }

    @Test
    fun fehltZweiterWertWirftException() {
        assertThatThrownBy { rechner.berechne(BigDecimal("100"), null, null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun nettoAusBruttoMitSiebenProzent() {
        val netto = rechner.nettoAusBrutto(BigDecimal("10.70"), BigDecimal("7"))

        assertThat(netto).isEqualByComparingTo("10.00")
    }

    @Test
    fun bruttoAusNettoMitNeunzehnProzent() {
        val brutto = rechner.bruttoAusNetto(BigDecimal("50.00"), BigDecimal("19"))

        assertThat(brutto).isEqualByComparingTo("59.50")
    }

    @Test
    fun nettoUndBruttoNullErgibtSatzNull() {
        val e = rechner.berechne(BigDecimal.ZERO, BigDecimal.ZERO, null)

        assertThat(e.satzProzent).isEqualByComparingTo("0.00")
        assertThat(e.mwstBetrag).isEqualByComparingTo("0.00")
    }

    @Test
    fun nettoNullMitBruttoUngleichNullWirftException() {
        assertThatThrownBy { rechner.berechne(BigDecimal.ZERO, BigDecimal("10"), null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun negativerSatzWirftException() {
        assertThatThrownBy { rechner.berechne(BigDecimal("100"), null, BigDecimal("-1")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun satzUeber100WirftException() {
        assertThatThrownBy { rechner.berechne(BigDecimal("100"), null, BigDecimal("150")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun riesigerBetragWirftException() {
        val absurd = BigDecimal("1e20")
        assertThatThrownBy { rechner.berechne(absurd, null, BigDecimal("19")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
