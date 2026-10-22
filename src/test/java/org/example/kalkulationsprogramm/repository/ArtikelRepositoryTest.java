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

    // ==================== Issue #265 ====================

    @Test
    void hoechsteAutomatischeArtikelnummer_ignoriertVonHandVergebene() {
        for (String nummer : java.util.List.of("A-004142", "A-009999", "A-010000", "A-Schraube-M8x40", "AL-FL-x100-20")) {
            Artikel a = new Artikel();
            a.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
            a.setArtikelnummer(nummer);
            artikelRepository.saveAndFlush(a);
        }

        assertThat(artikelRepository.findeHoechsteAutomatischeArtikelnummern(
                org.springframework.data.domain.PageRequest.of(0, 3)))
                .containsExactly("A-010000", "A-009999", "A-004142");
    }

    @Test
    void existsByArtikelnummerIgnoreCaseAndIdNot_zaehltNurAndere() {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.setArtikelnummer("A-000001");
        artikelRepository.saveAndFlush(artikel);

        assertThat(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("a-000001", artikel.getId())).isFalse();
        assertThat(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("a-000001", artikel.getId() + 1)).isTrue();
    }

    @Test
    void existsByArtikelnummerIgnoreCase() {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.setArtikelnummer("FS-40-5");
        artikelRepository.saveAndFlush(artikel);

        assertThat(artikelRepository.existsByArtikelnummerIgnoreCase("fs-40-5")).isTrue();
        assertThat(artikelRepository.existsByArtikelnummerIgnoreCase("FS-40-6")).isFalse();
    }

    @Test
    void sortiertNachGewichtJeMeterUndArtikelnummer() {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe schwer = new org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe();
        schwer.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        schwer.setProduktname("Schwer");
        schwer.setArtikelnummer("B-2");
        schwer.setMasse(new BigDecimal("7.85"));
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe leicht = new org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe();
        leicht.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        leicht.setProduktname("Leicht");
        leicht.setArtikelnummer("a-1");
        leicht.setMasse(new BigDecimal("1.57"));
        Artikel ohneGewicht = new Artikel();
        ohneGewicht.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        ohneGewicht.setProduktname("Ohne");
        ohneGewicht.setArtikelnummer("C-3");
        artikelRepository.saveAndFlush(schwer);
        artikelRepository.saveAndFlush(leicht);
        artikelRepository.saveAndFlush(ohneGewicht);

        java.util.List<String> nachGewicht = artikelRepository.findAll(PortableSortierung.mit(null,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "masse")))
                .stream().map(Artikel::getProduktname).toList();
        assertThat(nachGewicht).containsExactly("Schwer", "Leicht", "Ohne");

        java.util.List<String> nachNummer = artikelRepository.findAll(PortableSortierung.mit(null,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("artikelnummer").ignoreCase())))
                .stream().map(Artikel::getArtikelnummer).toList();
        assertThat(nachNummer).containsExactly("a-1", "B-2", "C-3");
    }
}
