package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.domain.Gewerk
import org.example.kalkulationsprogramm.dto.GewerkDto
import org.example.kalkulationsprogramm.repository.GewerkRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal

class GewerkServiceTest {

    private lateinit var repository: GewerkRepository
    private lateinit var service: GewerkService

    @BeforeEach
    fun setUp() {
        repository = mock(GewerkRepository::class.java)
        service = GewerkService(repository)
        `when`(repository.save(any(Gewerk::class.java))).thenAnswer { invocation ->
            val gewerk = invocation.getArgument<Gewerk>(0)
            if (gewerk.id == null) {
                gewerk.id = 33L
            }
            gewerk
        }
    }

    @Test
    fun leererNameWirdAbgewiesen() {
        val dto = GewerkDto().apply {
            bgName = "BG BAU"
            bgSatzProzent = BigDecimal("3.30")
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Name")
    }

    @Test
    fun leererBgNameWirdAbgewiesen() {
        val dto = GewerkDto().apply {
            name = "Tischler"
            bgSatzProzent = BigDecimal("1.13")
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("BG-Name")
    }

    @Test
    fun fehlenderBgSatzWirdAbgewiesen() {
        val dto = GewerkDto().apply {
            name = "Tischler"
            bgName = "BGHM"
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("BG-Satz")
    }

    @Test
    fun neuesGewerkWirdGespeichert() {
        val dto = GewerkDto().apply {
            name = "Tischler"
            bgName = "BGHM"
            bgSatzProzent = BigDecimal("1.13")
        }

        val saved = service.save(dto)

        assertThat(saved.id).isEqualTo(33L)
        assertThat(saved.name).isEqualTo("Tischler")
        assertThat(saved.bgName).isEqualTo("BGHM")
        assertThat(saved.bgSatzProzent).isEqualByComparingTo("1.13")
        assertThat(saved.aktiv).isTrue()
    }
}
