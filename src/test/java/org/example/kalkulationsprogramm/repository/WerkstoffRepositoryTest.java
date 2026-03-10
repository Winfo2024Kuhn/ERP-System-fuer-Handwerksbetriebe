package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WerkstoffRepositoryTest {

    @Autowired
    private WerkstoffRepository werkstoffRepository;

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;


    @Test
    void savesWerkstoffAndAssociatesArtikel() {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setName("Stahl");
        werkstoff = werkstoffRepository.save(werkstoff);

        Artikel artikel = new Artikel();
        artikel.setProduktlinie("Linie");
        artikel.setProduktname("Produkt");
        artikel.setProdukttext("Beschreibung");
        artikel.setVerpackungseinheit(1L);
        artikel.setPreiseinheit("Stk");
        artikel.setWerkstoff(werkstoff);

        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("L1");
        lieferantenRepository.save(lieferant);

        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setLieferant(lieferant);
        lap.setExterneArtikelnummer("A-1");
        artikel.getArtikelpreis().add(lap);

        artikelRepository.save(artikel);

        Artikel reloaded = artikelRepository.findById(artikel.getId()).orElseThrow();
        assertThat(reloaded.getWerkstoff()).isNotNull();
        assertThat(reloaded.getWerkstoff().getId()).isEqualTo(werkstoff.getId());
        assertThat(reloaded.getExterneArtikelnummer()).isEqualTo("A-1");
    }
}
