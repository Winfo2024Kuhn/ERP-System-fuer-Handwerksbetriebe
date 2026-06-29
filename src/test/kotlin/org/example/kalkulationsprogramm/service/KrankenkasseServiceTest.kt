package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.domain.Krankenkasse
import org.example.kalkulationsprogramm.dto.KrankenkasseDto
import org.example.kalkulationsprogramm.repository.KrankenkasseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.util.Optional

class KrankenkasseServiceTest {

    private lateinit var repository: KrankenkasseRepository
    private lateinit var service: KrankenkasseService

    @BeforeEach
    fun setUp() {
        repository = mock(KrankenkasseRepository::class.java)
        service = KrankenkasseService(repository)
        `when`(repository.save(any(Krankenkasse::class.java))).thenAnswer {
            it.getArgument<Krankenkasse>(0).apply {
                if (id == null) {
                    id = 42L
                }
            }
        }
    }

    @Test
    fun leererNameWirdAbgewiesen() {
        val dto = KrankenkasseDto().apply {
            name = "   "
            zusatzbeitragProzent = BigDecimal("2.5")
        }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Name")
    }

    @Test
    fun fehlenderZusatzbeitragWirdAbgewiesen() {
        val dto = KrankenkasseDto().apply { name = "Test-KK" }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Zusatzbeitrag")
    }

    @Test
    fun neueKrankenkasseWirdGespeichert() {
        val dto = KrankenkasseDto().apply {
            name = "Test-KK"
            kuerzel = "TKK"
            zusatzbeitragProzent = BigDecimal("2.50")
        }

        val saved = service.save(dto)

        assertThat(saved.id).isEqualTo(42L)
        assertThat(saved.name).isEqualTo("Test-KK")
        assertThat(saved.zusatzbeitragProzent).isEqualByComparingTo("2.50")
        assertThat(saved.aktiv).isTrue()
    }

    @Test
    fun updateUebernimmtFelderUndAktivFlag() {
        val bestehend = Krankenkasse().apply {
            id = 7L
            name = "Alt"
            zusatzbeitragProzent = BigDecimal("2.00")
            aktiv = true
        }
        `when`(repository.findById(7L)).thenReturn(Optional.of(bestehend))

        val dto = KrankenkasseDto().apply {
            id = 7L
            name = "Neu"
            zusatzbeitragProzent = BigDecimal("3.10")
            aktiv = false
        }

        val saved = service.save(dto)

        assertThat(saved.id).isEqualTo(7L)
        assertThat(saved.name).isEqualTo("Neu")
        assertThat(saved.zusatzbeitragProzent).isEqualByComparingTo("3.10")
        assertThat(saved.aktiv).isFalse()
    }

    @Test
    fun findAllSortiertNachName() {
        val a = krankenkasse(1L, "AOK")
        val t = krankenkasse(2L, "TK")
        `when`(repository.findAllByOrderByNameAsc()).thenReturn(listOf(a, t))

        val list = service.findAll()

        assertThat(list).extracting<String> { it.name }.containsExactly("AOK", "TK")
    }

    @Test
    fun findAktivLiefertNurAktive() {
        val a = krankenkasse(1L, "AOK")
        `when`(repository.findByAktivTrueOrderByNameAsc()).thenReturn(listOf(a))

        assertThat(service.findAktiv()).hasSize(1)
    }

    @Test
    fun deleteRuftRepoAufRichtigeId() {
        service.delete(99L)
        verify(repository).deleteById(99L)
    }

    private fun krankenkasse(id: Long, name: String): Krankenkasse =
        Krankenkasse().apply {
            this.id = id
            this.name = name
            zusatzbeitragProzent = BigDecimal("2.50")
            aktiv = true
        }
}
