package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.domain.SvSatz
import org.example.kalkulationsprogramm.domain.SvSatzTyp
import org.example.kalkulationsprogramm.dto.SvSatzDto
import org.example.kalkulationsprogramm.repository.SvSatzRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDate

class SvSatzServiceTest {

    private lateinit var repository: SvSatzRepository
    private lateinit var service: SvSatzService

    @BeforeEach
    fun setUp() {
        repository = mock(SvSatzRepository::class.java)
        service = SvSatzService(repository)
    }

    @Test
    fun unbekannterSatzTypWirdAbgewiesen() {
        val dto = SvSatzDto().apply {
            satzTyp = "ERFUNDEN_XY"
            prozent = BigDecimal("14.6")
            gueltigAb = LocalDate.of(2026, 1, 1)
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ERFUNDEN_XY")
    }

    @Test
    fun leererTypWirdAbgewiesen() {
        val dto = SvSatzDto().apply {
            prozent = BigDecimal("14.6")
            gueltigAb = LocalDate.of(2026, 1, 1)
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Typ")
    }

    @Test
    fun fehlendesGueltigAbWirdAbgewiesen() {
        val dto = SvSatzDto().apply {
            satzTyp = "KV_GESAMT"
            prozent = BigDecimal("14.6")
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Gueltig")
    }

    @Test
    fun doppelterTypAmGleichenDatumWirftSprechendeMeldung() {
        `when`(repository.saveAndFlush(any(SvSatz::class.java)))
            .thenThrow(DataIntegrityViolationException("uk_sv_satz_typ_ab"))

        val dto = SvSatzDto().apply {
            satzTyp = "KV_GESAMT"
            prozent = BigDecimal("14.6")
            gueltigAb = LocalDate.of(2026, 1, 1)
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("KV_GESAMT")
            .hasMessageContaining("2026-01-01")
    }

    @Test
    fun neuerSatzWirdGespeichert() {
        `when`(repository.saveAndFlush(any(SvSatz::class.java))).thenAnswer {
            it.getArgument<SvSatz>(0).apply { id = 11L }
        }

        val dto = SvSatzDto().apply {
            satzTyp = "PV_GESAMT"
            prozent = BigDecimal("3.40")
            gueltigAb = LocalDate.of(2026, 1, 1)
        }

        val saved = service.save(dto)

        assertThat(saved.id).isEqualTo(11L)
        assertThat(saved.satzTyp).isEqualTo("PV_GESAMT")
        assertThat(saved.prozent).isEqualByComparingTo("3.40")
    }

    @Test
    fun findAllListetSortiertAuf() {
        val s = SvSatz().apply {
            id = 1L
            satzTyp = SvSatzTyp.KV_GESAMT
            prozent = BigDecimal("14.60")
            gueltigAb = LocalDate.of(2026, 1, 1)
        }
        `when`(repository.findAllByOrderBySatzTypAscGueltigAbDesc()).thenReturn(listOf(s))

        assertThat(service.findAll()).hasSize(1)
    }
}
