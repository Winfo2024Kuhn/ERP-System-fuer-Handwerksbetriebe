package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal

class ProjektMapperTest {

    private val mapper = ProjektMapper(
        ProduktkategorieMapper(),
        AnfrageMapper(),
        mock(KundeMapper::class.java),
    )

    @Test
    fun mapsKilogrammOnArtikel() {
        val projekt = Projekt()
        val aip = ArtikelInProjekt().apply {
            kilogramm = BigDecimal("5.5")
        }
        projekt.artikelInProjekt.add(aip)

        val dto = requireNotNull(mapper.toProjektResponseDto(projekt))
        assertEquals(1, dto.artikel!!.size)
        assertEquals(0, dto.artikel!!.first().kilogramm!!.compareTo(BigDecimal("5.5")))
    }

    @Test
    fun updatesPreisProStueckWithSupplierPrice() {
        val projekt = Projekt()
        val artikel = Artikel().apply {
            verrechnungseinheit = Verrechnungseinheit.STUECK
        }

        val aip = ArtikelInProjekt().apply {
            this.artikel = artikel
            stueckzahl = 2
            preisProStueck = BigDecimal("5")
        }

        val lap = LieferantenArtikelPreise().apply {
            this.artikel = artikel
            preis = BigDecimal("7")
        }
        aip.lieferantenArtikelPreis = lap

        projekt.artikelInProjekt.add(aip)

        val dto = requireNotNull(mapper.toProjektResponseDto(projekt))
        assertEquals(0, dto.artikel!!.first().preisProStueck!!.compareTo(BigDecimal("14")))
    }

    @Test
    fun mapsAbgeschlossenField() {
        val projekt = Projekt()
        projekt.abgeschlossen = false

        val dto1 = requireNotNull(mapper.toProjektResponseDto(projekt))
        assertEquals(false, dto1.isAbgeschlossen, "Projekt should not be closed initially")

        projekt.abgeschlossen = true
        val dto2 = requireNotNull(mapper.toProjektResponseDto(projekt))
        assertEquals(true, dto2.isAbgeschlossen, "Projekt should be closed after setting abgeschlossen=true")
    }
}
