package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({OfferPriceService.class, OfferPriceServiceTest.HookMockConfig.class})
class OfferPriceServiceTest {

    @TestConfiguration
    static class HookMockConfig {
        @Bean
        ArtikelPreisHookService artikelPreisHookService() {
            return Mockito.mock(ArtikelPreisHookService.class);
        }
    }

    @Autowired
    private OfferPriceService offerPriceService;

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Autowired
    private LieferantenArtikelPreiseRepository lieferantenArtikelPreiseRepository;

    @Test
    void updatesPriceWhenMailIsNewer() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Supplier");
        lieferant.getKundenEmails().add("sup@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("A1");
        preis.setPreis(new BigDecimal("1.00"));
        preis.setPreisAenderungsdatum(new Date(0));
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("A1", "ST", new BigDecimal("2.50"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, now, List.of(item));

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("2.50"), updated.getPreis());
        assertEquals(now, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void ignoresOlderMail() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Supplier2");
        lieferant.getKundenEmails().add("old@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("B1");
        preis.setPreis(new BigDecimal("5.00"));
        Date existingDate = new Date();
        preis.setPreisAenderungsdatum(existingDate);
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("B1", "ST", new BigDecimal("4.00"), null, "Name");
        Date older = new Date(existingDate.getTime() - 1000);
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, older, List.of(item));

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.00"), updated.getPreis());
        assertEquals(existingDate, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertEquals(1, result.skipped().size());
        assertTrue(result.updated().isEmpty());
    }

    @Test
    void forceOverridesOlderMail() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("SupplierForce");
        lieferant.getKundenEmails().add("force@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("B2");
        preis.setPreis(new BigDecimal("5.00"));
        Date existingDate = new Date();
        preis.setPreisAenderungsdatum(existingDate);
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("B2", "ST", new BigDecimal("4.00"), null, "Name");
        Date older = new Date(existingDate.getTime() - 1000);
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, older, List.of(item), true);

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("4.00"), updated.getPreis());
        assertEquals(older, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void returnsUnmatchedCodesWhenArticleMissing() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Supplier3");
        lieferant.getKundenEmails().add("missing@example.com");
        lieferantenRepository.save(lieferant);

        OfferItem item = new OfferItem("X1", "ST", new BigDecimal("3.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, now, List.of(item));

        assertEquals(1, result.unmatched().size());
        assertEquals("X1", result.unmatched().getFirst().code());
        assertTrue(result.updated().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    @Test
    void doesNotCreatePriceForUnknownSupplierMapping() {
        Lieferanten supplierA = new Lieferanten();
        supplierA.setLieferantenname("SupplierA");
        supplierA.getKundenEmails().add("a@example.com");
        lieferantenRepository.save(supplierA);

        Lieferanten supplierB = new Lieferanten();
        supplierB.setLieferantenname("SupplierB");
        supplierB.getKundenEmails().add("b@example.com");
        lieferantenRepository.save(supplierB);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(supplierA);
        preis.setExterneArtikelnummer("C1");
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("C1", "ST", new BigDecimal("7.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(supplierB, now, List.of(item));

        assertEquals(1, result.unmatched().size());
        assertTrue(lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), supplierB.getId()).isEmpty());
        assertTrue(result.updated().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    @Test
    void matchesWhenSupplierInstanceDiffersButIdSame() {
        Lieferanten original = new Lieferanten();
        original.setLieferantenname("SupX");
        original.getKundenEmails().add("x@example.com");
        lieferantenRepository.save(original);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(original);
        preis.setExterneArtikelnummer("C9");
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        // simulate detached supplier instance with same id
        Lieferanten stub = new Lieferanten();
        stub.setId(original.getId());
        stub.setLieferantenname(original.getLieferantenname());

        OfferItem item = new OfferItem("C9", "ST", new BigDecimal("1.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(stub, now, List.of(item));

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), original.getId()).orElseThrow();
        assertEquals(new BigDecimal("1.00"), updated.getPreis());
        assertEquals(now, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void convertsTonPriceForSupplierId4() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        OfferPriceService service = new OfferPriceService(repo, Mockito.mock(ArtikelPreisHookService.class));

        Lieferanten supplier = new Lieferanten();
        supplier.setId(4L);
        supplier.setLieferantenname("SupTon");

        Artikel artikel = new Artikel();
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setArtikel(artikel);
        price.setLieferant(supplier);
        price.setExterneArtikelnummer("F6010");
        artikel.getArtikelpreis().add(price);

        Mockito.when(repo.findByExterneArtikelnummerAndLieferantId("F6010", 4L))
                .thenReturn(Optional.of(artikel));

        OfferItem item = new OfferItem("F6010", null, new BigDecimal("790.00"), null, "Name");
        List<OfferItem> items = new ArrayList<>(List.of(item));
        Date now = new Date();
        PriceUpdateResult result = service.updatePrices(supplier, now, items);

        assertEquals(0, price.getPreis().compareTo(new BigDecimal("0.79")));
        assertEquals("KG", items.getFirst().unit());
        assertEquals(0, items.getFirst().price().compareTo(new BigDecimal("0.79")));
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
        Mockito.verify(repo).save(artikel);
    }

    @Test
    void convertsExplicitTonUnitToKilogramForSupplierId4() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        OfferPriceService service = new OfferPriceService(repo, Mockito.mock(ArtikelPreisHookService.class));

        Lieferanten supplier = new Lieferanten();
        supplier.setId(4L);
        supplier.setLieferantenname("SupTon");

        Artikel artikel = new Artikel();
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setArtikel(artikel);
        price.setLieferant(supplier);
        price.setExterneArtikelnummer("F6011");
        artikel.getArtikelpreis().add(price);

        Mockito.when(repo.findByExterneArtikelnummerAndLieferantId("F6011", 4L))
                .thenReturn(Optional.of(artikel));

        OfferItem item = new OfferItem("F6011", "TO", new BigDecimal("980.00"), null, "Name");
        List<OfferItem> items = new ArrayList<>(List.of(item));
        Date now = new Date();
        PriceUpdateResult result = service.updatePrices(supplier, now, items);

        assertEquals(0, price.getPreis().compareTo(new BigDecimal("0.9800")));
        assertEquals("KG", items.getFirst().unit());
        assertEquals(0, items.getFirst().price().compareTo(new BigDecimal("0.9800")));
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
        Mockito.verify(repo).save(artikel);
    }

    @Test
    void convertsTonPriceForSupplierId4WhenUnitBlank() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        OfferPriceService service = new OfferPriceService(repo, Mockito.mock(ArtikelPreisHookService.class));

        Lieferanten supplier = new Lieferanten();
        supplier.setId(4L);
        supplier.setLieferantenname("SupTon");

        Artikel artikel = new Artikel();
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setArtikel(artikel);
        price.setLieferant(supplier);
        price.setExterneArtikelnummer("F6010");
        artikel.getArtikelpreis().add(price);

        Mockito.when(repo.findByExterneArtikelnummerAndLieferantId("F6010", 4L))
                .thenReturn(Optional.of(artikel));

        OfferItem item = new OfferItem("F6010", "", new BigDecimal("790.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = service.updatePrices(supplier, now, List.of(item));

        assertEquals(0, price.getPreis().compareTo(new BigDecimal("0.79")));
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
        Mockito.verify(repo).save(artikel);
    }

    @Test
    void matchesWhenCodeIgnoresCaseAndWhitespace() {
        Lieferanten supplier = new Lieferanten();
        supplier.setLieferantenname("SupTrim");
        supplier.getKundenEmails().add("trim@example.com");
        lieferantenRepository.save(supplier);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(supplier);
        preis.setExterneArtikelnummer("T2");
        preis.setPreis(new BigDecimal("1.00"));
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("  t2  ", "ST", new BigDecimal("3.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(supplier, now, List.of(item));

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikel.getId(), supplier.getId()).orElseThrow();
        assertEquals(new BigDecimal("3.00"), updated.getPreis());
        assertEquals(now, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void selectsCorrectSupplierWhenCodesOverlap() {
        Lieferanten supplierA = new Lieferanten();
        supplierA.setLieferantenname("OverlapA");
        supplierA.getKundenEmails().add("oa@example.com");
        lieferantenRepository.save(supplierA);

        Artikel artikelA = new Artikel();
        artikelA.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preisA = new LieferantenArtikelPreise();
        preisA.setArtikel(artikelA);
        preisA.setLieferant(supplierA);
        preisA.setExterneArtikelnummer("DUP1");
        preisA.setPreis(new BigDecimal("1.00"));
        preisA.setPreisAenderungsdatum(new Date(0));
        artikelA.getArtikelpreis().add(preisA);
        artikelRepository.save(artikelA);

        Lieferanten supplierB = new Lieferanten();
        supplierB.setLieferantenname("OverlapB");
        supplierB.getKundenEmails().add("ob@example.com");
        lieferantenRepository.save(supplierB);

        Artikel artikelB = new Artikel();
        artikelB.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preisB = new LieferantenArtikelPreise();
        preisB.setArtikel(artikelB);
        preisB.setLieferant(supplierB);
        preisB.setExterneArtikelnummer("DUP1");
        preisB.setPreis(new BigDecimal("2.00"));
        preisB.setPreisAenderungsdatum(new Date(0));
        artikelB.getArtikelpreis().add(preisB);
        artikelRepository.save(artikelB);

        OfferItem item = new OfferItem("DUP1", "ST", new BigDecimal("5.00"), null, "Name");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(supplierB, now, List.of(item));

        LieferantenArtikelPreise updatedB = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikelB.getId(), supplierB.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.00"), updatedB.getPreis());
        assertEquals(now, updatedB.getPreisAenderungsdatum());

        LieferantenArtikelPreise unchangedA = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikelA.getId(), supplierA.getId()).orElseThrow();
        assertEquals(new BigDecimal("1.00"), unchangedA.getPreis());

        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void continuesWhenDuplicateEntryOccurs() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        OfferPriceService service = new OfferPriceService(repo, Mockito.mock(ArtikelPreisHookService.class));

        Lieferanten supplier = new Lieferanten();
        supplier.setId(1L);
        supplier.setLieferantenname("DupSup");

        Artikel artikel = new Artikel();
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setArtikel(artikel);
        price.setLieferant(supplier);
        price.setExterneArtikelnummer("DUPX");
        artikel.getArtikelpreis().add(price);

        Mockito.when(repo.findByExterneArtikelnummerAndLieferantId("DUPX", supplier.getId()))
                .thenReturn(Optional.of(artikel));
        Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(repo).save(artikel);

        OfferItem item = new OfferItem("DUPX", "ST", new BigDecimal("9.99"), null, "Name");
        PriceUpdateResult result = service.updatePrices(supplier, new Date(), List.of(item));

        assertEquals(1, result.unmatched().size());
        assertEquals("DUPX", result.unmatched().getFirst().code());
        assertTrue(result.updated().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    // Hinweis (A2 Phase 1β): Der frühere Test `updatesProjektArtikelWithNewPrice`
    // prüfte die Propagation neuer Preise auf `ArtikelInProjekt` (setLieferant /
    // setPreisProStueck / setLieferantenArtikelPreis). Dieser Code-Pfad ist
    // entfernt — OfferPriceService aktualisiert nur noch den Artikelstamm plus
    // den ArtikelPreisHook. Die Kalkulation läuft ab jetzt ausschliesslich über
    // die Eingangsrechnung + Bestellung.
}
