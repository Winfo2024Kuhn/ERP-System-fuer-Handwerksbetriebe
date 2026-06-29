package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus
import org.example.kalkulationsprogramm.domain.BelegPosition
import org.example.kalkulationsprogramm.repository.BelegPositionRepository
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class BelegSplitServiceTest {

    @Mock
    private lateinit var belegRepository: BelegRepository

    @Mock
    private lateinit var belegPositionRepository: BelegPositionRepository

    private lateinit var service: BelegSplitService

    @BeforeEach
    fun setUp() {
        service = BelegSplitService(belegRepository, belegPositionRepository, MwstRechnerService())
    }

    @Test
    fun recomputeFirmaSummenSummiertNurAngehaktePositionen() {
        val beleg = Beleg().apply {
            id = 1L
            aufteilungsModus = BelegAufteilungsModus.TEILWEISE
        }
        val kaffee = pos(1L, "Kaffee", BigDecimal("4.99"), BigDecimal("19"), true)
        val brot = pos(2L, "Brot", BigDecimal("2.50"), BigDecimal("7"), false)
        val wasser = pos(3L, "Wasser", BigDecimal("1.99"), BigDecimal("19"), true)

        `when`(belegPositionRepository.findByBelegIdOrderBySortierungAsc(1L))
            .thenReturn(listOf(kaffee, brot, wasser))

        service.recomputeFirmaSummen(beleg)

        assertThat(beleg.betragFirmaBrutto).isEqualByComparingTo("6.98")
        assertThat(beleg.betragFirmaNetto).isEqualByComparingTo("5.86")
        assertThat(beleg.betragFirmaMwst).isEqualByComparingTo("1.12")
    }

    @Test
    fun recomputeBeiVollstaendigLeertFirmaFelder() {
        val beleg = Beleg().apply {
            id = 2L
            aufteilungsModus = BelegAufteilungsModus.VOLLSTAENDIG
            betragFirmaNetto = BigDecimal("10.00")
        }

        service.recomputeFirmaSummen(beleg)

        assertThat(beleg.betragFirmaNetto).isNull()
        assertThat(beleg.betragFirmaBrutto).isNull()
        assertThat(beleg.betragFirmaMwst).isNull()
    }

    @Test
    fun aktualisiereAuswahlWirftBeiVollstaendigMode() {
        val beleg = Beleg().apply {
            id = 3L
            aufteilungsModus = BelegAufteilungsModus.VOLLSTAENDIG
        }
        `when`(belegRepository.findById(3L)).thenReturn(Optional.of(beleg))

        assertThatThrownBy { service.aktualisiereAuswahl(3L, setOf(1L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("TEILWEISE")
    }

    @Test
    fun aktualisiereAuswahlSetztNurMarkiertePositionenAufFirma() {
        val beleg = Beleg().apply {
            id = 4L
            aufteilungsModus = BelegAufteilungsModus.TEILWEISE
        }
        val p1 = pos(10L, "A", BigDecimal("10.00"), BigDecimal("19"), false)
        val p2 = pos(11L, "B", BigDecimal("20.00"), BigDecimal("19"), true)

        `when`(belegRepository.findById(4L)).thenReturn(Optional.of(beleg))
        `when`(belegPositionRepository.findByBelegIdOrderBySortierungAsc(4L)).thenReturn(listOf(p1, p2))
        `when`(belegRepository.save(any(Beleg::class.java))).thenAnswer { it.getArgument(0) }

        service.aktualisiereAuswahl(4L, setOf(10L))

        assertThat(p1.istFuerFirma).isTrue()
        assertThat(p2.istFuerFirma).isFalse()
    }

    private fun pos(id: Long, beschreibung: String, brutto: BigDecimal, satz: BigDecimal, firma: Boolean): BelegPosition =
        BelegPosition().apply {
            this.id = id
            this.beschreibung = beschreibung
            betragBrutto = brutto
            mwstSatz = satz
            istFuerFirma = firma
        }
}
