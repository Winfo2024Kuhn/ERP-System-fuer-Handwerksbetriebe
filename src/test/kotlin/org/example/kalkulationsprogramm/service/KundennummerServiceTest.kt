package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.KundenZaehler
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.KundenZaehlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional

class KundennummerServiceTest {

    private lateinit var kundeRepository: KundeRepository
    private lateinit var kundenZaehlerRepository: KundenZaehlerRepository
    private lateinit var service: KundennummerService

    @BeforeEach
    fun setUp() {
        kundeRepository = mock(KundeRepository::class.java)
        kundenZaehlerRepository = mock(KundenZaehlerRepository::class.java)
        service = KundennummerService(kundeRepository, kundenZaehlerRepository)
    }

    @Test
    fun reserviereLiefertAktuelleZaehlerNummerUndInkrementiert() {
        val zaehler = KundenZaehler().apply {
            id = 1
            naechsteNummer = 1042L
        }
        given(kundenZaehlerRepository.lockAndGet()).willReturn(zaehler)

        val nummer = service.reserviereNaechsteKundennummer()

        assertThat(nummer).isEqualTo("1042")
        assertThat(zaehler.naechsteNummer).isEqualTo(1043L)
        verify(kundeRepository, never()).findMaxKundennummer()
    }

    @Test
    fun reserviereFaelltAufMaxPlusEinsZurueckWennZaehlerFehlt() {
        given(kundenZaehlerRepository.lockAndGet()).willReturn(null)
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.of("1099"))

        val nummer = service.reserviereNaechsteKundennummer()

        assertThat(nummer).isEqualTo("1100")
    }

    @Test
    fun generiereStartetBei1000WennNochKeinKunde() {
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.empty())

        assertThat(service.generiereNaechsteKundennummer()).isEqualTo("1000")
    }

    @Test
    fun generiereSpringtAufFallbackBeiNichtNumerischerNummer() {
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.of("ABC"))

        assertThat(service.generiereNaechsteKundennummer()).isEqualTo("1000")
    }
}
