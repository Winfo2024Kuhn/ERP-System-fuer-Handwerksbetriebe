package org.example.kalkulationsprogramm.mapper

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.Arbeitsgang
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ArbeitsgangMapperTest {

    @Mock
    private lateinit var stundensatzRepository: ArbeitsgangStundensatzRepository

    @InjectMocks
    private lateinit var mapper: ArbeitsgangMapper

    @Test
    fun mapptAlleFelder() {
        val abteilung = Abteilung().apply {
            id = 3L
            name = "Schlosserei"
        }

        val arbeitsgang = Arbeitsgang().apply {
            id = 1L
            beschreibung = "Schweißen"
            this.abteilung = abteilung
        }

        val stundensatz = ArbeitsgangStundensatz().apply {
            satz = BigDecimal("72.50")
            jahr = 2025
        }

        `when`(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(1L))
            .thenReturn(Optional.of(stundensatz))

        val dto = mapper.toArbeitsgangResponseDto(arbeitsgang)

        assertThat(dto!!.id).isEqualTo(1L)
        assertThat(dto!!.beschreibung).isEqualTo("Schweißen")
        assertThat(dto!!.abteilungId).isEqualTo(3L)
        assertThat(dto!!.abteilungName).isEqualTo("Schlosserei")
        assertThat(dto!!.stundensatz).isEqualByComparingTo(BigDecimal("72.50"))
        assertThat(dto!!.stundensatzJahr).isEqualTo(2025)
    }

    @Test
    fun gibtNullZurueckBeiNullArbeitsgang() {
        assertThat(mapper.toArbeitsgangResponseDto(null)).isNull()
    }

    @Test
    fun mapptArbeitsgangOhneAbteilung() {
        val arbeitsgang = Arbeitsgang().apply {
            id = 2L
            beschreibung = "Test"
            abteilung = null
        }

        `when`(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(2L))
            .thenReturn(Optional.empty())

        val dto = mapper.toArbeitsgangResponseDto(arbeitsgang)

        assertThat(dto!!.abteilungId).isNull()
        assertThat(dto!!.abteilungName).isNull()
    }

    @Test
    fun mapptArbeitsgangOhneStundensatz() {
        val arbeitsgang = Arbeitsgang().apply {
            id = 3L
            beschreibung = "Ohne Satz"
        }

        `when`(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(3L))
            .thenReturn(Optional.empty())

        val dto = mapper.toArbeitsgangResponseDto(arbeitsgang)

        assertThat(dto!!.stundensatz).isNull()
        assertThat(dto!!.stundensatzJahr).isNull()
    }
}
