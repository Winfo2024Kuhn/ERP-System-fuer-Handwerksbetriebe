package org.example.kalkulationsprogramm.repository

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Werkstoff
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class WerkstoffRepositoryTest {

    @Autowired
    private lateinit var werkstoffRepository: WerkstoffRepository

    @Autowired
    private lateinit var artikelRepository: ArtikelRepository

    @Autowired
    private lateinit var lieferantenRepository: LieferantenRepository

    @Test
    fun savesWerkstoffAndAssociatesArtikel() {
        val werkstoff = werkstoffRepository.save(Werkstoff().apply {
            name = "Stahl"
        })

        val artikel = Artikel().apply {
            produktlinie = "Linie"
            produktname = "Produkt"
            produkttext = "Beschreibung"
            verpackungseinheit = 1L
            preiseinheit = "Stk"
            this.werkstoff = werkstoff
        }

        val lieferant = Lieferanten().apply {
            lieferantenname = "L1"
        }
        lieferantenRepository.save(lieferant)

        val lap = LieferantenArtikelPreise().apply {
            this.artikel = artikel
            this.lieferant = lieferant
            externeArtikelnummer = "A-1"
        }
        artikel.artikelpreis.add(lap)

        artikelRepository.save(artikel)

        val reloaded = artikelRepository.findById(artikel.id).orElseThrow()
        assertThat(reloaded.werkstoff).isNotNull()
        assertThat(reloaded.werkstoff.id).isEqualTo(werkstoff.id)
        assertThat(reloaded.externeArtikelnummer).isEqualTo("A-1")
    }
}
