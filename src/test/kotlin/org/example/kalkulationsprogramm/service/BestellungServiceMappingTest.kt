package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

class BestellungServiceMappingTest {

    @Test
    fun mapsAnglesAndFormIntoDto() {
        val repo = mock(ArtikelInProjektRepository::class.java)
        val service = BestellungService(repo)

        val kat = Kategorie().apply {
            id = 2
            beschreibung = "Formstahl"
        }
        val artikel = Artikel().apply {
            id = 10L
            externeArtikelnummer = "X-123"
            produktname = "Profil"
            produkttext = "Fixzuschnitt"
            kategorie = kat
        }

        val aip = ArtikelInProjekt().apply {
            id = 5L
            this.artikel = artikel
            stueckzahl = 3
            kilogramm = BigDecimal("12.5")
            hinzugefuegtAm = LocalDate.now()
            kommentar = "Test"
            schnittForm = "A"
            anschnittWinkelLinks = "45"
            anschnittWinkelRechts = "90"
        }

        `when`(repo.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
            .thenReturn(listOf(aip))

        val dtos = service.findeOffeneBestellungen()
        assertEquals(1, dtos.size)
        val dto = dtos.first()
        assertEquals("A", dto.schnittForm)
        assertEquals("45", dto.anschnittWinkelLinks)
        assertEquals("90", dto.anschnittWinkelRechts)
        assertEquals("Test", dto.kommentar)
    }
}
