package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ArtikelCascadeDeletionTest {
    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Autowired
    private LieferantenArtikelPreiseRepository lieferantenArtikelPreiseRepository;

    @Test
    void deletingArtikelRemovesSupplierPrices() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Supplier");
        lieferantenRepository.saveAndFlush(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("EXT-1");
        preis.setPreis(BigDecimal.ONE);
        artikel.getArtikelpreis().add(preis);

        artikelRepository.saveAndFlush(artikel);

        assertEquals(1, lieferantenArtikelPreiseRepository.count());

        artikelRepository.delete(artikel);
        artikelRepository.flush();

        assertEquals(0, lieferantenArtikelPreiseRepository.count());
    }
}
