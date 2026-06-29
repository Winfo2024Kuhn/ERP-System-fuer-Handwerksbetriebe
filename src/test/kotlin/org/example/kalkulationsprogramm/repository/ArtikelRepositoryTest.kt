package org.example.kalkulationsprogramm.repository

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.math.BigDecimal

@DataJpaTest
class ArtikelRepositoryTest {

    @Autowired
    private lateinit var artikelRepository: ArtikelRepository

    @Autowired
    private lateinit var lieferantenRepository: LieferantenRepository

    @Test
    fun findByExterneArtikelnummerIgnoresCaseAndWhitespace() {
        val supplier = Lieferanten().apply {
            lieferantenname = "SupplierA"
        }
        lieferantenRepository.saveAndFlush(supplier)

        val artikel = Artikel().apply {
            verrechnungseinheit = Verrechnungseinheit.STUECK
        }
        val preis = LieferantenArtikelPreise().apply {
            this.artikel = artikel
            lieferant = supplier
            externeArtikelnummer = "ABC123"
            this.preis = BigDecimal.ONE
        }
        artikel.artikelpreis.add(preis)
        artikelRepository.saveAndFlush(artikel)

        assertThat(artikelRepository.findByExterneArtikelnummer("  abc123  ")).isPresent()
    }

    @Test
    fun findByExterneArtikelnummerAndLieferantIdIgnoresCaseAndWhitespace() {
        val supplier = Lieferanten().apply {
            lieferantenname = "Supplier"
        }
        lieferantenRepository.saveAndFlush(supplier)

        val artikel = Artikel().apply {
            verrechnungseinheit = Verrechnungseinheit.STUECK
        }
        val preis = LieferantenArtikelPreise().apply {
            this.artikel = artikel
            lieferant = supplier
            externeArtikelnummer = "XYZ789"
            this.preis = BigDecimal.ONE
        }
        artikel.artikelpreis.add(preis)
        artikelRepository.saveAndFlush(artikel)

        assertThat(artikelRepository.findByExterneArtikelnummerAndLieferantId("  xyz789  ", supplier.id)).isPresent()
    }
}
