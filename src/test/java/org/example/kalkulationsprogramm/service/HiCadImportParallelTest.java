package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.example.kalkulationsprogramm.domain.einkauf.HiCadImport;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImportZeile;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.HiCadImportRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = HiCadImportParallelTest.TestConfig.class)
class HiCadImportParallelTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("hicad_import_dummy").withUsername("test").withPassword("test");

    @jakarta.annotation.Resource HiCadImportRepository imports;
    @jakarta.annotation.Resource HiCadImportService service;
    @jakarta.annotation.Resource org.springframework.transaction.PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        imports.deleteAll();
        org.mockito.Mockito.clearInvocations(TestConfig.BEDARFE, TestConfig.DATEIEN);
    }

    @Test
    void parallelRetryCreatesOneNeedThenProgressVersionAllowsRemainder() throws Exception {
        HiCadImport imported = new HiCadImport(17L, "b".repeat(64), 4L, false);
        HiCadImportZeile row = new HiCadImportZeile(3, "HiCAD row",
                "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":10,\"einheit\":\"STUECK\",\"stueckzahl\":10},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setBildDateiIdsJson("[7]");
        imported.addZeile(row);
        imported = imports.saveAndFlush(imported);
        long importId = imported.getId();

        UUID key = UUID.randomUUID();
        var request = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new BigDecimal("4"), null, List.of(7L))), false, key);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> callTogether(importId, request, ready, start));
            var second = pool.submit(() -> callTogether(importId, request, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var firstResult = first.get(30, TimeUnit.SECONDS);
            var secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(firstResult, secondResult);
            assertEquals(firstResult.get(0).id(), secondResult.get(0).id());
        }

        HiCadImport stored = imports.findById(importId).orElseThrow();
        assertEquals(new BigDecimal("4.000000"), stored.getZeilen().get(0).getUebernommeneMenge());
        assertEquals(false, stored.getZeilen().get(0).isUebernommen());
        assertTrue(stored.getVersion() > 0);
        var progress = service.fortschritt(importId, 4L);
        assertEquals(stored.getVersion(), progress.version());
        assertEquals(new BigDecimal("6.000000"), progress.zeilen().get(0).verbleibendeMenge());

        service.uebernehmen(importId, new HiCadImportDto.Uebernahme(progress.version(),
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new BigDecimal("6"), null, List.of(7L))), false,
                UUID.randomUUID()), 4L);
        HiCadImport completed = imports.findById(importId).orElseThrow();
        assertEquals(new BigDecimal("10.000000"), completed.getZeilen().get(0).getUebernommeneMenge());
        assertTrue(completed.getZeilen().get(0).isUebernommen());
        verify(TestConfig.BEDARFE, org.mockito.Mockito.times(2)).anlegen(any(), eq(4L));
    }

    @Test
    void mysqlPersistsLargePartialQuantityAndItsRemainder() {
        org.mockito.Mockito.clearInvocations(TestConfig.BEDARFE, TestConfig.DATEIEN);
        HiCadImport imported = new HiCadImport(17L, "c".repeat(64), 4L, false);
        HiCadImportZeile row = new HiCadImportZeile(4, "Large HiCAD row",
                "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-18\",\"zeichnungsnummer\":\"Z-18\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Rahmen\",\"basis\":{\"menge\":1000000000,\"einheit\":\"STUECK\",\"stueckzahl\":1000000000},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setBildDateiIdsJson("[7]");
        when(TestConfig.DATEIEN.anhaengenImportBild(any(), eq(7L), any(), eq(4L)))
                .thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto(
                        700L, 70L, 71L, "HiCAD-test", "test.png", "image/png", 9L, "hash", false, false, null));
        imported.addZeile(row);
        imported = imports.saveAndFlush(imported);
        long importId = imported.getId();

        var first = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(4, new BigDecimal("400000000"), null, List.of(7L))), false, UUID.randomUUID());
        service.uebernehmen(importId, first, 4L);

        HiCadImport afterPartial = imports.findById(importId).orElseThrow();
        assertEquals(new BigDecimal("400000000.000000"), afterPartial.getZeilen().get(0).getUebernommeneMenge());
        var progress = service.fortschritt(importId, 4L);
        assertEquals(new BigDecimal("1000000000.000000"), progress.zeilen().get(0).gesamtmenge());
        assertEquals(new BigDecimal("600000000.000000"), progress.zeilen().get(0).verbleibendeMenge());
        assertEquals(false, progress.zeilen().get(0).vollstaendigUebernommen());

        service.uebernehmen(importId, new HiCadImportDto.Uebernahme(progress.version(),
                List.of(new HiCadImportDto.ZeilenAuswahl(4, new BigDecimal("600000000"), null, List.of(7L))), false,
                UUID.randomUUID()), 4L);

        HiCadImport completed = imports.findById(importId).orElseThrow();
        assertEquals(new BigDecimal("1000000000.000000"), completed.getZeilen().get(0).getUebernommeneMenge());
        assertTrue(completed.getZeilen().get(0).isUebernommen());
    }

    @Test
    void geloeschterBedarfOeffnetSeineImportzeileWiederFuerDieNaechsteUebernahme() {
        HiCadImport imported = new HiCadImport(19L, "d".repeat(64), 4L, false);
        HiCadImportZeile row = new HiCadImportZeile(5, "HiCAD row",
                "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-19\",\"zeichnungsnummer\":\"Z-19\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Dummy Lasche\",\"basis\":{\"menge\":10,\"einheit\":\"STUECK\",\"stueckzahl\":10},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setBildDateiIdsJson("[7]");
        imported.addZeile(row);
        long importId = imports.saveAndFlush(imported).getId();

        var created = service.uebernehmen(importId, new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(5, new BigDecimal("10"), null, List.of(7L))), false,
                UUID.randomUUID()), 4L);
        assertTrue(imports.findById(importId).orElseThrow().getZeilen().get(0).isUebernommen());
        Long bedarfId = created.get(0).id();

        var freigaben = new TransactionTemplate(transactionManager)
                .execute(status -> service.gibUebernahmeFrei(19L, bedarfId, null));
        assertEquals(1, freigaben.size());
        assertEquals(5, freigaben.get(0).zeilennummer());
        assertEquals(0, new BigDecimal("10").compareTo(freigaben.get(0).menge()));
        HiCadImport reopened = imports.findById(importId).orElseThrow();
        assertEquals(0, reopened.getZeilen().get(0).getUebernommeneMenge().signum());
        assertEquals(false, reopened.getZeilen().get(0).isUebernommen());
        // Ein zweites Freigeben desselben Bedarfs ändert nichts mehr.
        assertTrue(new TransactionTemplate(transactionManager)
                .execute(status -> service.gibUebernahmeFrei(19L, bedarfId, null)).isEmpty());

        var progress = service.fortschritt(importId, 4L);
        assertEquals(0, new BigDecimal("10").compareTo(progress.zeilen().get(0).verbleibendeMenge()));
        service.uebernehmen(importId, new HiCadImportDto.Uebernahme(progress.version(),
                List.of(new HiCadImportDto.ZeilenAuswahl(5, new BigDecimal("10"), null, List.of(7L))), false,
                UUID.randomUUID()), 4L);
        assertTrue(imports.findById(importId).orElseThrow().getZeilen().get(0).isUebernommen());
    }

    @Test
    void aeltereUebernahmeOhneZuordnungWirdUeberEindeutigePositionsnummerFreigegeben() throws Exception {
        HiCadImport imported = new HiCadImport(21L, "e".repeat(64), 4L, false);
        HiCadImportZeile row = new HiCadImportZeile(6, "HiCAD row",
                "{\"art\":\"FREITEXT\",\"bezeichnung\":\"Dummy Flachstahl\",\"positionsnummer\":\"1200\",\"basis\":{\"menge\":10,\"einheit\":\"STUECK\",\"stueckzahl\":10},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setUebernommeneMenge(new BigDecimal("10"));
        row.setUebernommen(true);
        imported.addZeile(row);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String result = json.writeValueAsString(List.of(new EinkaufBedarfDto.Response(88L, 1L, null, null, null, false, null)));
        imported.setIdempotenzErgebnisseJson(json.writeValueAsString(List.of(
                java.util.Map.of("idempotenzKey", "alt", "payloadHash", "h", "resultJson", result))));
        long importId = imports.saveAndFlush(imported).getId();
        // In kg übernommener Freitext: die Stückzahl der Stückzeile zählt.
        var position = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot(
                org.example.kalkulationsprogramm.domain.einkauf.Positionsart.FREITEXT, null, null, null, null,
                "Dummy Flachstahl", null, null, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(
                        new BigDecimal("42"), org.example.kalkulationsprogramm.domain.einkauf.Einheit.KILOGRAMM,
                        new BigDecimal("10"), null, null, null), null, null, null, null, null, List.of(), List.of(), null, "1200");

        assertTrue(new TransactionTemplate(transactionManager)
                .execute(status -> service.gibUebernahmeFrei(21L, 99L, position)).isEmpty());
        var freigaben = new TransactionTemplate(transactionManager)
                .execute(status -> service.gibUebernahmeFrei(21L, 88L, position));

        assertEquals(1, freigaben.size());
        HiCadImport reopened = imports.findById(importId).orElseThrow();
        assertEquals(0, reopened.getZeilen().get(0).getUebernommeneMenge().signum());
        assertEquals(false, reopened.getZeilen().get(0).isUebernommen());
    }

    private List<EinkaufBedarfDto.Response> callTogether(long id, HiCadImportDto.Uebernahme request,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { assertTrue(start.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        return service.uebernehmen(id, request, 4L);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository",
            excludeFilters = @Filter(type = FilterType.REGEX,
                    pattern = "org\\.example\\.kalkulationsprogramm\\.repository\\.(?!HiCadImportRepository$).*$"))
    static class TestConfig {
        static final EinkaufBedarfService BEDARFE = mock(EinkaufBedarfService.class);
        static final EinkaufDateiService DATEIEN = mock(EinkaufDateiService.class);

        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
            factory.setManagedTypes(PersistenceManagedTypes.of(HiCadImport.class.getName(), HiCadImportZeile.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create",
                    "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean EinkaufBedarfService bedarfService() {
            AtomicLong ids = new AtomicLong(70L);
            when(BEDARFE.anlegen(any(), eq(4L))).thenAnswer(invocation ->
                    new EinkaufBedarfDto.Response(ids.getAndIncrement(), 0L, null, null, null, false, null));
            when(BEDARFE.aktualisieren(any(), any(), eq(4L))).thenAnswer(invocation ->
                    new EinkaufBedarfDto.Response(invocation.getArgument(0), 1L, null, null, null, false, null));
            when(DATEIEN.anhaengenImportBild(any(), eq(7L), any(), eq(4L))).thenAnswer(invocation ->
                    new EinkaufDateiDto.AnlageDto(80L, 90L, invocation.getArgument(0), invocation.getArgument(2),
                            "image.png", "image/png", 9L, "hash", false, false, null));
            return BEDARFE;
        }

        @Bean EinkaufDateiService dateiService() { return DATEIEN; }
        @Bean ArtikelRepository artikelRepository() { return mock(ArtikelRepository.class); }
        @Bean HiCadImportService hiCadImportService(HiCadImportRepository repository,
                EinkaufBedarfService bedarfService, EinkaufDateiService dateiService, ArtikelRepository artikelRepository) {
            return new HiCadImportService(repository, bedarfService, dateiService, artikelRepository);
        }
    }
}
