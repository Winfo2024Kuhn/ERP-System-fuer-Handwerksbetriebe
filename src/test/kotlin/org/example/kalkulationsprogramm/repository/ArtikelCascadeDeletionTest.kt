package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.math.BigDecimal

@DataJpaTest
class ArtikelCascadeDeletionTest {

    @Autowired
    private lateinit var artikelRepository: ArtikelRepository

    @Autowired
    private lateinit var lieferantenRepository: LieferantenRepository

    @Autowired
    private lateinit var lieferantenArtikelPreiseRepository: LieferantenArtikelPreiseRepository

    @Test
    fun deletingArtikelRemovesSupplierPrices() {
        val lieferant = Lieferanten().apply {
            lieferantenname = "Supplier"
        }
        lieferantenRepository.saveAndFlush(lieferant)

        val artikel = Artikel().apply {
            verrechnungseinheit = Verrechnungseinheit.STUECK
        }

        val preis = LieferantenArtikelPreise().apply {
            this.artikel = artikel
            this.lieferant = lieferant
            externeArtikelnummer = "EXT-1"
            preis = BigDecimal.ONE
        }
        artikel.artikelpreis.add(preis)

        artikelRepository.saveAndFlush(artikel)

        assertEquals(1, lieferantenArtikelPreiseRepository.count())

        artikelRepository.delete(artikel)
        artikelRepository.flush()

        assertEquals(0, lieferantenArtikelPreiseRepository.count())
    }
}
