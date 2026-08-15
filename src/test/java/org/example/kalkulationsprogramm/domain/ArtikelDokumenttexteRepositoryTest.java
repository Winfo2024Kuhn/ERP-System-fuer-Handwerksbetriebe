package org.example.kalkulationsprogramm.domain;

import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ArtikelDokumenttexteRepositoryTest {

    @Autowired
    private ArtikelRepository artikelRepository;

    @Test
    void speichertUndLaedtDieDokumenttexte() {
        Artikel artikel = new Artikel();
        artikel.setProduktname("Rundrohr");
        artikel.setKurzbeschreibung("Rundrohr 42,4 x 2,0 Lager");
        artikel.setBeschreibung("<p>Rundrohr, Edelstahl 1.4301, 42,4 x 2,0 mm</p>");
        artikel.setVerkaufsaufschlagProzent(new BigDecimal("40.00"));

        Artikel gespeichert = artikelRepository.saveAndFlush(artikel);
        Artikel geladen = artikelRepository.findById(gespeichert.getId()).orElseThrow();

        assertThat(geladen.getKurzbeschreibung()).isEqualTo("Rundrohr 42,4 x 2,0 Lager");
        assertThat(geladen.getBeschreibung()).contains("1.4301");
        assertThat(geladen.getVerkaufsaufschlagProzent()).isEqualByComparingTo("40.00");
    }

    @Test
    void ungepflegteFelderBleibenLeer() {
        Artikel artikel = new Artikel();
        artikel.setProduktname("Flachstahl");

        Artikel geladen = artikelRepository.findById(
                artikelRepository.saveAndFlush(artikel).getId()).orElseThrow();

        assertThat(geladen.getKurzbeschreibung()).isNull();
        assertThat(geladen.getBeschreibung()).isNull();
        assertThat(geladen.getVerkaufsaufschlagProzent()).isNull();
    }
}
