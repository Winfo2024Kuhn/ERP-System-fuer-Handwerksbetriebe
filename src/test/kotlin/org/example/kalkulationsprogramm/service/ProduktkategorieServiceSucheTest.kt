package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ProduktkategorieServiceSucheTest {

    @Mock
    private lateinit var produktkategorieRepository: ProduktkategorieRepository

    @Mock
    private lateinit var projektRepository: ProjektRepository

    @Mock
    private lateinit var dateiSpeicherService: DateiSpeicherService

    private lateinit var service: ProduktkategorieService

    @BeforeEach
    fun setUp() {
        service = ProduktkategorieService(
            produktkategorieRepository,
            projektRepository,
            ProduktkategorieMapper(),
            dateiSpeicherService,
        )
    }

    @Test
    fun sucheLeafKategorienGibtErgebnisseZurueck() {
        val stahl = Produktkategorie().apply {
            id = 1L
            bezeichnung = "Edelstahl"
            verrechnungseinheit = Verrechnungseinheit.KILOGRAMM
        }

        `when`(produktkategorieRepository.sucheLeafKategorienNachBezeichnung("stahl"))
            .thenReturn(listOf(stahl))

        val ergebnis = service.sucheLeafKategorien("stahl")

        assertThat(ergebnis).hasSize(1)
        assertThat(ergebnis[0].bezeichnung).isEqualTo("Edelstahl")
        assertThat(ergebnis[0].isLeaf).isTrue()
    }

    @Test
    fun sucheLeafKategorienGibtLeereListeBeiLeeremSuchbegriff() {
        val ergebnis = service.sucheLeafKategorien("")

        assertThat(ergebnis).isEmpty()
        verify(produktkategorieRepository, never()).sucheLeafKategorienNachBezeichnung(anyString())
    }

    @Test
    fun sucheLeafKategorienGibtLeereListeBeiNull() {
        val ergebnis = service.sucheLeafKategorien(null)

        assertThat(ergebnis).isEmpty()
        verify(produktkategorieRepository, never()).sucheLeafKategorienNachBezeichnung(anyString())
    }

    @Test
    fun sucheLeafKategorienTrimmtSuchbegriff() {
        `when`(produktkategorieRepository.sucheLeafKategorienNachBezeichnung("stahl"))
            .thenReturn(emptyList())

        service.sucheLeafKategorien("  stahl  ")

        verify(produktkategorieRepository).sucheLeafKategorienNachBezeichnung("stahl")
    }
}
