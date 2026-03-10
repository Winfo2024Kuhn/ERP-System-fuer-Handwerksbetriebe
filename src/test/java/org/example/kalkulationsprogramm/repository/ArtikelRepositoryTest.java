package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ArtikelRepositoryTest {

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Test
    void findByExterneArtikelnummerIgnoresCaseAndWhitespace() {
        Lieferanten supplier = new Lieferanten();
        supplier.setLieferantenname("SupplierA");
        lieferantenRepository.saveAndFlush(supplier);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(supplier);
        preis.setExterneArtikelnummer("ABC123");
        preis.setPreis(BigDecimal.ONE);
        artikel.getArtikelpreis().add(preis);
        artikelRepository.saveAndFlush(artikel);

        assertThat(artikelRepository.findByExterneArtikelnummer("  abc123  ")).isPresent();
    }

    @Test
    void findByExterneArtikelnummerAndLieferantIdIgnoresCaseAndWhitespace() {
        Lieferanten supplier = new Lieferanten();
        supplier.setLieferantenname("Supplier");
        lieferantenRepository.saveAndFlush(supplier);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(supplier);
        preis.setExterneArtikelnummer("XYZ789");
        preis.setPreis(BigDecimal.ONE);
        artikel.getArtikelpreis().add(preis);
        artikelRepository.saveAndFlush(artikel);

        assertThat(artikelRepository.findByExterneArtikelnummerAndLieferantId("  xyz789  ", supplier.getId())).isPresent();
    }
}
