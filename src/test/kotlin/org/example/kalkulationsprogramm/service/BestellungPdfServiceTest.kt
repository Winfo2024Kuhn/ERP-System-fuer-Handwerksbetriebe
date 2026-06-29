package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class BestellungPdfServiceTest {

    @Test
    fun createsPdfFile() {
        val bestellungService = mock(BestellungService::class.java)
        val dto = BestellungResponseDto().apply {
            lieferantId = 1L
            projektName = "Projekt A"
            externeArtikelnummer = "123"
            produktname = "Produkt"
            produkttext = "Text"
            kommentar = "Kommentar"
            menge = BigDecimal.ONE
            einheit = "Stk"
        }
        `when`(bestellungService.findeOffeneBestellungen()).thenReturn(listOf(dto))

        val schnittbilderRepository = mock(SchnittbilderRepository::class.java)
        val dateiSpeicherService = mock(DateiSpeicherService::class.java)
        val firmeninformationService = mock(FirmeninformationService::class.java)
        `when`(firmeninformationService.loadLogoImage()).thenReturn(null)
        val service = BestellungPdfService(
            bestellungService,
            schnittbilderRepository,
            dateiSpeicherService,
            firmeninformationService,
        )

        val pdf = service.generatePdfForLieferant(1L)
        assertTrue(Files.size(pdf) > 0)
        val content = Files.readString(pdf, StandardCharsets.ISO_8859_1)
        assertTrue(content.contains("Bauvorhaben:"))
        assertTrue(content.contains("Rechnungen separat pro Auftrag"))
        Files.deleteIfExists(pdf)
    }

    @Test
    fun createsPdfFileForUnknownLieferant() {
        val bestellungService = mock(BestellungService::class.java)
        val dto = BestellungResponseDto().apply {
            lieferantId = null
            projektName = "Projekt B"
            externeArtikelnummer = "456"
            produktname = "ProduktB"
            produkttext = "TextB"
            kommentar = "KommentarB"
            menge = BigDecimal.ONE
            einheit = "Stk"
        }
        `when`(bestellungService.findeOffeneBestellungen()).thenReturn(listOf(dto))

        val schnittbilderRepository = mock(SchnittbilderRepository::class.java)
        val dateiSpeicherService = mock(DateiSpeicherService::class.java)
        val firmeninformationService = mock(FirmeninformationService::class.java)
        `when`(firmeninformationService.loadLogoImage()).thenReturn(null)
        val service = BestellungPdfService(
            bestellungService,
            schnittbilderRepository,
            dateiSpeicherService,
            firmeninformationService,
        )

        val pdf = service.generatePdfForLieferant(null)
        assertTrue(Files.size(pdf) > 0)
        Files.deleteIfExists(pdf)
    }

    @Test
    fun createsPdfForProjekt() {
        val bestellungService = mock(BestellungService::class.java)
        val dto = BestellungResponseDto().apply {
            projektId = 7L
            projektName = "Projekt C"
            rootKategorieId = 1
            externeArtikelnummer = "789"
            produktname = "ProdC"
            produkttext = "TextC"
            kommentar = "KommentarC"
            menge = BigDecimal.ONE
            einheit = "Stk"
        }
        `when`(bestellungService.findeOffeneBestellungen()).thenReturn(listOf(dto))

        val schnittbilderRepository = mock(SchnittbilderRepository::class.java)
        val dateiSpeicherService = mock(DateiSpeicherService::class.java)
        val firmeninformationService = mock(FirmeninformationService::class.java)
        `when`(firmeninformationService.loadLogoImage()).thenReturn(null)
        val service = BestellungPdfService(
            bestellungService,
            schnittbilderRepository,
            dateiSpeicherService,
            firmeninformationService,
        )

        val pdf = service.generatePdfForProjekt(7L)
        assertTrue(Files.size(pdf) > 0)
        Files.deleteIfExists(pdf)
    }
}
