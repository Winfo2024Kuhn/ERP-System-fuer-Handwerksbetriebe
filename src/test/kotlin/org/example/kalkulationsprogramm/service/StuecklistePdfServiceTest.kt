package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.domain.Werkstoff
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Optional

class StuecklistePdfServiceTest {

    @Test
    fun createsPdfWithWerkstoffMm() {
        val projektRepository = mock(ProjektRepository::class.java)
        val aipRepo = mock(ArtikelInProjektRepository::class.java)
        val schnittbilderRepository = mock(SchnittbilderRepository::class.java)
        val dateiSpeicherService = mock(DateiSpeicherService::class.java)

        val projekt = Projekt().apply {
            id = 10L
            bauvorhaben = "BV X"
            auftragsnummer = "A-123"
            kundenId = Kunde().apply {
                name = "Muster GmbH"
                kundennummer = "K-TEST"
            }
        }
        `when`(projektRepository.findById(10L)).thenReturn(Optional.of(projekt))

        val rootWerk = Kategorie().apply {
            id = 1
            beschreibung = "Werkstoffe"
        }
        val subWerk = Kategorie().apply {
            id = 11
            beschreibung = "Profile"
            parentKategorie = rootWerk
        }

        val werkstoff = Werkstoff().apply {
            name = "S235"
        }

        val artikel = ArtikelWerkstoffe().apply {
            id = 5L
            produktname = "U-Profil"
            kategorie = subWerk
            this.werkstoff = werkstoff
            verrechnungseinheit = Verrechnungseinheit.LAUFENDE_METER
        }

        val aip = ArtikelInProjekt().apply {
            id = 99L
            this.projekt = projekt
            this.artikel = artikel
            stueckzahl = 5
            meter = BigDecimal("12.5")
            kommentar = "Zuschnitt"
        }

        `when`(aipRepo.findByProjekt_Id(10L)).thenReturn(listOf(aip))

        val service = StuecklistePdfService(projektRepository, aipRepo, schnittbilderRepository, dateiSpeicherService)
        val pdf = service.generateForProjekt(10L)
        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty())
        val content = String(pdf, StandardCharsets.ISO_8859_1)
        assertTrue(content.contains("Bauvorhaben:"))
        assertTrue(content.lowercase().contains("stueckliste") || content.contains("Stückliste"))
    }
}
