package org.example.kalkulationsprogramm.service.miete

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsart
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand
import org.example.kalkulationsprogramm.repository.miete.KostenpositionRepository
import org.example.kalkulationsprogramm.repository.miete.MieteKostenstelleRepository
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MietabrechnungServiceTest {

    @Mock
    private lateinit var mietobjektRepository: MietobjektRepository

    @Mock
    private lateinit var kostenstelleRepository: MieteKostenstelleRepository

    @Mock
    private lateinit var kostenpositionRepository: KostenpositionRepository

    @Mock
    private lateinit var verbrauchsgegenstandRepository: VerbrauchsgegenstandRepository

    @Mock
    private lateinit var zaehlerstandRepository: ZaehlerstandRepository

    @Mock
    private lateinit var kostenpositionBerechner: KostenpositionBerechner

    @InjectMocks
    private lateinit var service: MietabrechnungService

    private lateinit var mietobjekt: Mietobjekt
    private lateinit var gegenstand: Verbrauchsgegenstand

    @BeforeEach
    fun setUp() {
        mietobjekt = Mietobjekt().apply {
            id = 1L
            name = "Test Objekt"
            strasse = "Teststraße 1"
            plz = "12345"
            ort = "Testort"
        }
        gegenstand = Verbrauchsgegenstand().apply {
            id = 10L
            name = "Wasserzähler"
            verbrauchsart = Verbrauchsart.WASSER
            einheit = "m³"
        }
    }

    @Test
    fun berechneJahresabrechnung_shouldCalculateVorjahrConsumption_whenReadingsExist() {
        val year = 2023
        `when`(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt))
        `when`(kostenpositionRepository.findByKostenstelleMietobjektIdAndAbrechnungsJahr(anyLong(), anyInt()))
            .thenReturn(listOf())
        `when`(kostenstelleRepository.findByMietobjektIdOrderByNameAsc(anyLong())).thenReturn(listOf())
        `when`(verbrauchsgegenstandRepository.findByRaumMietobjektId(1L)).thenReturn(listOf(gegenstand))

        val z2023 = createZaehlerstand(2023, BigDecimal("350"))
        val z2022 = createZaehlerstand(2022, BigDecimal("200"))
        val z2021 = createZaehlerstand(2021, BigDecimal("100"))

        `when`(zaehlerstandRepository.findByVerbrauchsgegenstandInAndAbrechnungsJahr(anyList(), eq(2023)))
            .thenReturn(listOf(z2023))
        `when`(zaehlerstandRepository.findByVerbrauchsgegenstandInAndAbrechnungsJahr(anyList(), eq(2022)))
            .thenReturn(listOf(z2022))
        `when`(zaehlerstandRepository.findByVerbrauchsgegenstandAndAbrechnungsJahr(eq(gegenstand), eq(2021)))
            .thenReturn(Optional.of(z2021))

        val result = service.berechneJahresabrechnung(1L, year)

        assertThat(result.verbrauchsvergleiche).hasSize(1)
        val vv = result.verbrauchsvergleiche.first()
        assertThat(vv.verbrauchJahr).isEqualByComparingTo(BigDecimal("150"))
        assertThat(vv.verbrauchVorjahr).isEqualByComparingTo(BigDecimal("100"))
        assertThat(vv.differenz).isEqualByComparingTo(BigDecimal("50"))
    }

    private fun createZaehlerstand(year: Int, stand: BigDecimal): Zaehlerstand =
        Zaehlerstand().apply {
            id = year.toLong()
            verbrauchsgegenstand = gegenstand
            abrechnungsJahr = year
            this.stand = stand
            stichtag = LocalDate.of(year, 12, 31)
        }
}
