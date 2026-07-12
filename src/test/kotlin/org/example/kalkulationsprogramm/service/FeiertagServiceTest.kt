package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Feiertag
import org.example.kalkulationsprogramm.repository.FeiertagRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class FeiertagServiceTest {

    @Mock
    private lateinit var feiertagRepository: FeiertagRepository

    @InjectMocks
    private lateinit var feiertagService: FeiertagService

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(feiertagService, "self", feiertagService)
    }

    @Test
    fun istFeiertag_WennFeiertagExistiert_GibtTrueZurueck() {
        val weihnachten = LocalDate.of(2024, 12, 25)
        `when`(feiertagRepository.findByJahr(2024))
            .thenReturn(listOf(Feiertag(weihnachten, "1. Weihnachtstag", "BY")))
        `when`(feiertagRepository.existsByDatumAndBundesland(weihnachten, "BY")).thenReturn(true)

        val result = feiertagService.istFeiertag(weihnachten)

        assertTrue(result, "Weihnachten sollte als Feiertag erkannt werden")
    }

    @Test
    fun istFeiertag_WennKeinFeiertag_GibtFalseZurueck() {
        val normalerTag = LocalDate.of(2024, 7, 15)
        `when`(feiertagRepository.findByJahr(2024)).thenReturn(emptyList())
        `when`(feiertagRepository.saveAll(any<Iterable<Feiertag>>())).thenReturn(emptyList())
        `when`(feiertagRepository.existsByDatumAndBundesland(normalerTag, "BY")).thenReturn(false)

        val result = feiertagService.istFeiertag(normalerTag)

        assertFalse(result, "Ein normaler Arbeitstag sollte kein Feiertag sein")
    }

    @Test
    fun istFeiertag_Neujahr_GibtTrueZurueck() {
        val neujahr = LocalDate.of(2025, 1, 1)
        `when`(feiertagRepository.findByJahr(2025))
            .thenReturn(listOf(Feiertag(neujahr, "Neujahr", "BY")))
        `when`(feiertagRepository.existsByDatumAndBundesland(neujahr, "BY")).thenReturn(true)

        val result = feiertagService.istFeiertag(neujahr)

        assertTrue(result, "Neujahr sollte als Feiertag erkannt werden")
    }

    @Test
    fun getFeiertageZwischen_GibtKorrekteFeiertageListe() {
        val von = LocalDate.of(2024, 12, 1)
        val bis = LocalDate.of(2024, 12, 31)
        val dezemberFeiertage = listOf(
            Feiertag(LocalDate.of(2024, 12, 25), "1. Weihnachtstag", "BY"),
            Feiertag(LocalDate.of(2024, 12, 26), "2. Weihnachtstag", "BY"),
        )

        `when`(feiertagRepository.findByJahr(2024)).thenReturn(dezemberFeiertage)
        `when`(feiertagRepository.findByDatumBetween(von, bis)).thenReturn(dezemberFeiertage)

        val result = feiertagService.getFeiertageZwischen(von, bis)

        assertEquals(2, result.size, "Dezember sollte 2 Feiertage haben")
        assertEquals("1. Weihnachtstag", result.first().bezeichnung)
        assertEquals("2. Weihnachtstag", result[1].bezeichnung)
    }

    @Test
    fun getFeiertageForJahr_WennNichtVorhanden_LaedtVonApiOderGeneriertLokal() {
        `when`(feiertagRepository.findByJahr(2025)).thenReturn(emptyList())
        `when`(feiertagRepository.saveAll(any<Iterable<Feiertag>>())).thenAnswer { it.getArgument(0) }

        feiertagService.getFeiertageForJahr(2025)

        verify(feiertagRepository).saveAll(any<Iterable<Feiertag>>())
    }
}
