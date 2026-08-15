package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(OfferPriceService.class)
class OfferPriceServiceTest {

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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.00"), updated.getPreis());
        assertEquals(existingDate, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertEquals(1, result.skipped().size());
        assertTrue(result.updated().isEmpty());
    }

    @Test
    void unveraenderterPreisLegtKeinenNeuenStandAn() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterlieferant GmbH");
        lieferant.getKundenEmails().add("musterlieferant@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("MUSTER-001");
        preis.setPreis(new BigDecimal("5.00"));
        preis.setPreisAenderungsdatum(new Date(0));
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        // Angebot, Auftragsbestaetigung und Rechnung nennen zur selben Sache oft
        // denselben Preis - hier kommt die Angebots-Mail mit unverändertem Betrag.
        OfferItem item = new OfferItem("MUSTER-001", "ST", new BigDecimal("5.00"), null, "Muster-Artikel");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, now, List.of(item));

        LieferantenArtikelPreise unchanged = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.00"), unchanged.getPreis());
        assertEquals(new Date(0), unchanged.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.updated().isEmpty());
        assertEquals(1, result.skipped().size());
        assertEquals(1, artikelRepository.findById(artikel.getId()).orElseThrow().getArtikelpreis().size());
    }

    @Test
    void unveraenderterPreisInAndererSkalaLegtKeinenNeuenStandAn() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterlieferant GmbH");
        lieferant.getKundenEmails().add("musterlieferant2@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("MUSTER-002");
        // Bestand liegt noch mit zwei Nachkommastellen vor (vor Migration V359).
        preis.setPreis(new BigDecimal("5.00"));
        preis.setPreisAenderungsdatum(new Date(0));
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        // Die Mail liefert denselben Betrag, aber mit vier Nachkommastellen -
        // equals() wuerde das faelschlich als Aenderung werten, compareTo() nicht.
        OfferItem item = new OfferItem("MUSTER-002", "ST", new BigDecimal("5.0000"), null, "Muster-Artikel");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, now, List.of(item));

        LieferantenArtikelPreise unchanged = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(0, unchanged.getPreis().compareTo(new BigDecimal("5.00")));
        assertEquals(new Date(0), unchanged.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.updated().isEmpty());
        assertEquals(1, result.skipped().size());
        assertEquals(1, artikelRepository.findById(artikel.getId()).orElseThrow().getArtikelpreis().size());
    }

    @Test
    void geaenderterPreisLegtWeiterhinNeuenStandAn() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterlieferant GmbH");
        lieferant.getKundenEmails().add("musterlieferant3@example.com");
        lieferantenRepository.save(lieferant);

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setExterneArtikelnummer("MUSTER-003");
        preis.setPreis(new BigDecimal("5.00"));
        preis.setPreisAenderungsdatum(new Date(0));
        artikel.getArtikelpreis().add(preis);
        artikelRepository.save(artikel);

        OfferItem item = new OfferItem("MUSTER-003", "ST", new BigDecimal("6.50"), null, "Muster-Artikel");
        Date now = new Date();
        PriceUpdateResult result = offerPriceService.updatePrices(lieferant, now, List.of(item));

        LieferantenArtikelPreise updated = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(new BigDecimal("6.50"), updated.getPreis());
        assertEquals(now, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
        assertEquals(2, artikelRepository.findById(artikel.getId()).orElseThrow().getArtikelpreis().size());
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), supplierB.getId()).isEmpty());
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), original.getId()).orElseThrow();
        assertEquals(new BigDecimal("1.00"), updated.getPreis());
        assertEquals(now, updated.getPreisAenderungsdatum());
        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void convertsTonPriceForSupplierId4() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        OfferPriceService service = new OfferPriceService(repo, aipRepo);

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

        // Der neue Preis steht in einem neuen Preisstand, der bisherige bleibt
        // als Historie erhalten und gilt nicht mehr als aktuell.
        LieferantenArtikelPreise aktuellerStand = artikel.getAktuellePreise().getFirst();
        assertEquals(0, aktuellerStand.getPreis().compareTo(new BigDecimal("0.79")));
        assertFalse(price.isAktuell());
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
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        OfferPriceService service = new OfferPriceService(repo, aipRepo);

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

        LieferantenArtikelPreise aktuellerStand = artikel.getAktuellePreise().getFirst();
        assertEquals(0, aktuellerStand.getPreis().compareTo(new BigDecimal("0.9800")));
        assertFalse(price.isAktuell());
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
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        OfferPriceService service = new OfferPriceService(repo, aipRepo);

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

        LieferantenArtikelPreise aktuellerStand = artikel.getAktuellePreise().getFirst();
        assertEquals(0, aktuellerStand.getPreis().compareTo(new BigDecimal("0.79")));
        assertFalse(price.isAktuell());
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), supplier.getId()).orElseThrow();
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
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikelB.getId(), supplierB.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.00"), updatedB.getPreis());
        assertEquals(now, updatedB.getPreisAenderungsdatum());

        LieferantenArtikelPreise unchangedA = lieferantenArtikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikelA.getId(), supplierA.getId()).orElseThrow();
        assertEquals(new BigDecimal("1.00"), unchangedA.getPreis());

        assertTrue(result.unmatched().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertEquals(1, result.updated().size());
    }

    @Test
    void continuesWhenDuplicateEntryOccurs() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        OfferPriceService service = new OfferPriceService(repo, aipRepo);

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

    @Test
    void updatesProjektArtikelWithNewPrice() {
        ArtikelRepository repo = Mockito.mock(ArtikelRepository.class);
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        OfferPriceService service = new OfferPriceService(repo, aipRepo);

        Lieferanten supplier = new Lieferanten();
        supplier.setId(7L);
        supplier.setLieferantenname("SupProj");

        Artikel artikel = new Artikel();
        artikel.setId(11L);
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setArtikel(artikel);
        price.setLieferant(supplier);
        price.setExterneArtikelnummer("PX1");
        artikel.getArtikelpreis().add(price);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setLieferant(supplier);
        aip.setStueckzahl(3);
        aip.setLieferantenArtikelPreis(price);

        Mockito.when(repo.findByExterneArtikelnummerAndLieferantId("PX1", 7L))
                .thenReturn(Optional.of(artikel));
        Mockito.when(repo.save(artikel)).thenAnswer(i -> i.getArgument(0));
        Mockito.when(aipRepo.findByArtikel_IdAndLieferant_IdAndBestelltFalse(11L, 7L))
                .thenReturn(List.of(aip));
        Mockito.when(aipRepo.saveAll(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        OfferItem item = new OfferItem("PX1", "ST", new BigDecimal("2.00"), null, "Name");
        service.updatePrices(supplier, new Date(), List.of(item));

        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("6.00")));
        assertSame(price, aip.getLieferantenArtikelPreis());
    }
}
