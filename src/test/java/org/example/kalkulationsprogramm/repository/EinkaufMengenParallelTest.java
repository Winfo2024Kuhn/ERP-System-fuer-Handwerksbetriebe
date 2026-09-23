package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMengenService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EinkaufMengenParallelTest.TestConfig.class)
class EinkaufMengenParallelTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_mengen_dummy")
            .withUsername("test")
            .withPassword("test");

    @jakarta.annotation.Resource EinkaufBedarfRepository bedarfRepository;
    @jakarta.annotation.Resource EinkaufMengenbuchungRepository buchungRepository;
    @jakarta.annotation.Resource EinkaufMengenService mengenService;

    @AfterEach
    void cleanup() {
        buchungRepository.deleteAllInBatch();
        bedarfRepository.deleteAll();
    }

    @Test
    void zweiParalleleReservierungenKoennenGemeinsamenBedarfNichtUeberziehen() throws Exception {
        EinkaufBedarf bedarf = bedarfRepository.saveAndFlush(bedarf("10"));

        List<Boolean> results = runTogether(bedarf.getId(), bedarf.getVersion(),
                EinkaufMengenService.Mengenaktion.RESERVIEREN, EinkaufMengenService.Mengenaktion.RESERVIEREN);

        assertExactlyOneSucceeded(results);
        var stand = mengenService.stand(bedarf.getId());
        assertEquals(0, stand.reserviert().compareTo(new BigDecimal("6.000000")));
        assertEquals(0, stand.disponierbar().compareTo(new BigDecimal("4.000000")));
    }

    @Test
    void paralleleLagerentnahmeUndReservierungNutzenDieselbeSperre() throws Exception {
        EinkaufBedarf bedarf = bedarfRepository.saveAndFlush(bedarf("10"));

        List<Boolean> results = runTogether(bedarf.getId(), bedarf.getVersion(),
                EinkaufMengenService.Mengenaktion.LAGER_ENTNEHMEN, EinkaufMengenService.Mengenaktion.RESERVIEREN);

        assertExactlyOneSucceeded(results);
        var stand = mengenService.stand(bedarf.getId());
        assertEquals(0, stand.lagergedeckt().add(stand.reserviert()).compareTo(new BigDecimal("6.000000")));
        assertEquals(0, stand.disponierbar().compareTo(new BigDecimal("4.000000")));
    }

    @Test
    void lieferungVerringertOffenenBedarfNichtZweimalUndIdempotenzVerhindertDoppelbuchung() {
        EinkaufBedarf bedarf = bedarfRepository.saveAndFlush(bedarf("10"));
        UUID reservierungKey = UUID.randomUUID();
        Herkunft reservierung = new Herkunft(bedarf.getId(), bedarf.getVersion(), new BigDecimal("4"));
        mengenService.buche(List.of(reservierung), EinkaufMengenService.Mengenaktion.RESERVIEREN,
                "ANFRAGE-1", reservierungKey, 1L);
        var nachReservierung = mengenService.stand(bedarf.getId());
        assertEquals(0, nachReservierung.ungedeckt().compareTo(new BigDecimal("10.000000")));
        assertEquals(0, nachReservierung.disponierbar().compareTo(new BigDecimal("6.000000")));
        // Same request retry returns the original result even though the entity version advanced.
        mengenService.buche(List.of(reservierung), EinkaufMengenService.Mengenaktion.RESERVIEREN,
                "ANFRAGE-1", reservierungKey, 1L);
        assertEquals(1, buchungRepository.findAllByIdempotenzKey(reservierungKey).size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> mengenService.buche(List.of(new Herkunft(bedarf.getId(), bedarf.getVersion(),
                                new BigDecimal("3"))), EinkaufMengenService.Mengenaktion.RESERVIEREN,
                        "ANFRAGE-1", reservierungKey, 1L));

        Long aktuelleVersion = bedarfRepository.findById(bedarf.getId()).orElseThrow().getVersion();
        mengenService.buche(List.of(new Herkunft(bedarf.getId(), aktuelleVersion, new BigDecimal("4"))),
                EinkaufMengenService.Mengenaktion.BESTELLEN, "BESTELLUNG-1", UUID.randomUUID(), 1L);
        aktuelleVersion = bedarfRepository.findById(bedarf.getId()).orElseThrow().getVersion();
        mengenService.buche(List.of(new Herkunft(bedarf.getId(), aktuelleVersion, new BigDecimal("2"))),
                EinkaufMengenService.Mengenaktion.LIEFERN, "LIEFERUNG-1", UUID.randomUUID(), 1L);

        var stand = mengenService.stand(bedarf.getId());
        assertEquals(0, stand.bestellt().compareTo(new BigDecimal("4.000000")));
        assertEquals(0, stand.geliefert().compareTo(new BigDecimal("2.000000")));
        assertEquals(0, stand.ungedeckt().compareTo(new BigDecimal("6.000000")));
    }

    @Test
    void ungueltigerZweiterAnteilRolltGesamteMehrbedarfsbuchungZurueck() {
        EinkaufBedarf erster = bedarfRepository.saveAndFlush(bedarf("10"));
        EinkaufBedarf zweiter = bedarfRepository.saveAndFlush(bedarf("5"));
        zweiter.setNachpflegeErforderlich(true);
        zweiter = bedarfRepository.saveAndFlush(zweiter);
        Long zweiterId = zweiter.getId();

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> mengenService.buche(List.of(
                        new Herkunft(erster.getId(), erster.getVersion(), new BigDecimal("4")),
                        new Herkunft(zweiterId, bedarfRepository.findById(zweiterId).orElseThrow().getVersion(),
                                new BigDecimal("2"))),
                        EinkaufMengenService.Mengenaktion.RESERVIEREN, "MEHRBEDARF-1", UUID.randomUUID(), 1L));

        assertEquals(0, mengenService.stand(erster.getId()).reserviert().compareTo(BigDecimal.ZERO));
        assertEquals(0, mengenService.stand(zweiterId).reserviert().compareTo(BigDecimal.ZERO));
        assertEquals(0, buchungRepository.count());
    }

    @Test
    void migrationUebernimmtLegacyAipEinmaligUndKennzeichnetHistorischeFlags() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("CREATE TABLE projekt (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE artikel (id BIGINT PRIMARY KEY, artikelnummer VARCHAR(64), produktname VARCHAR(255), verrechnungseinheit VARCHAR(32))");
        jdbc.execute("CREATE TABLE artikel_in_projekt (id BIGINT PRIMARY KEY, projekt_id BIGINT, artikel_id BIGINT, stueckzahl INT, meter DECIMAL(19,2), kilogramm DECIMAL(19,2), bestellt BIT(1), aus_lager BIT(1), schnitt_form VARCHAR(80), anschnitt_winkel_links VARCHAR(80), anschnitt_winkel_rechts VARCHAR(80), kommentar VARCHAR(255))");
        jdbc.update("INSERT INTO projekt(id) VALUES (1)");
        jdbc.update("INSERT INTO artikel(id, artikelnummer, produktname, verrechnungseinheit) VALUES (11,'D-11','Testbolzen','STUECK')");
        jdbc.update("INSERT INTO artikel_in_projekt VALUES (101,1,11,10,NULL,NULL,b'0',b'0',NULL,NULL,NULL,NULL)");
        jdbc.update("INSERT INTO artikel_in_projekt VALUES (102,1,11,3,NULL,NULL,b'1',b'0',NULL,NULL,NULL,NULL)");
        jdbc.update("INSERT INTO artikel_in_projekt VALUES (103,1,11,2,NULL,NULL,b'1',b'1',NULL,NULL,NULL,NULL)");
        jdbc.update("INSERT INTO artikel_in_projekt VALUES (104,1,999,4,NULL,NULL,b'0',b'0',NULL,NULL,NULL,NULL)");

        try {
            var migration = new ClassPathResource("db/migration/V378__einkauf_bedarf_mengen.sql");
            try (var connection = jdbc.getDataSource().getConnection()) {
                ScriptUtils.executeSqlScript(connection, migration);
                ScriptUtils.executeSqlScript(connection, migration);
            }

            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM einkauf_bedarf", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT bedarf_menge FROM einkauf_bedarf WHERE artikel_in_projekt_id=101", BigDecimal.class).compareTo(new BigDecimal("10.000000")));
            assertTrue(jdbc.queryForObject("SELECT historisch_bestellt FROM einkauf_bedarf WHERE artikel_in_projekt_id=102", Boolean.class));
            assertTrue(jdbc.queryForObject("SELECT historisch_aus_lager FROM einkauf_bedarf WHERE artikel_in_projekt_id=103", Boolean.class));
            assertTrue(jdbc.queryForObject("SELECT nachpflege_erforderlich FROM einkauf_bedarf WHERE artikel_in_projekt_id=104", Boolean.class));
            assertEquals(0, jdbc.queryForObject("SELECT bestellt FROM einkauf_bedarf WHERE artikel_in_projekt_id=102", BigDecimal.class).compareTo(BigDecimal.ZERO));
            assertNull(jdbc.queryForObject("SELECT bedarf_menge FROM einkauf_bedarf WHERE artikel_in_projekt_id=104", BigDecimal.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM einkauf_mengenbuchung", Integer.class));
        } finally {
            jdbc.execute("DROP TABLE artikel_in_projekt");
            jdbc.execute("DROP TABLE artikel");
            jdbc.execute("DROP TABLE projekt");
        }
    }

    private List<Boolean> runTogether(Long id, Long version, EinkaufMengenService.Mengenaktion first,
            EinkaufMengenService.Mengenaktion second) throws Exception {
        var result = new ConcurrentLinkedQueue<Boolean>();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        for (var action : List.of(first, second)) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    mengenService.buche(List.of(new Herkunft(id, version, new BigDecimal("6"))), action,
                            "TEST-" + action.name(), UUID.randomUUID(), 1L);
                    result.add(true);
                } catch (Exception expected) {
                    result.add(false);
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        return List.copyOf(result);
    }

    private static void assertExactlyOneSucceeded(List<Boolean> results) {
        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, results.stream().filter(value -> !value).count());
    }

    private static EinkaufBedarf bedarf(String amount) {
        BigDecimal quantity = new BigDecimal(amount);
        PositionSnapshot snapshot = new PositionSnapshot(Positionsart.ARTIKEL, 1L, "DUMMY-1", null, null,
                "Testprofil", null, null, new Mengenbasis(quantity, Einheit.STUECK, quantity, null, null, null),
                null, null, null, null, null, null, null);
        return new EinkaufBedarf(snapshot, new Liefergruppe(null, null, null, "Testlager"), null, null, false);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository",
            excludeFilters = @Filter(type = FilterType.REGEX,
                    pattern = "org\\.example\\.kalkulationsprogramm\\.repository\\.(?!EinkaufBedarfRepository$|EinkaufMengenbuchungRepository$).*$"))
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
            factory.setManagedTypes(PersistenceManagedTypes.of(EinkaufBedarf.class.getName(),
                    org.example.kalkulationsprogramm.domain.einkauf.EinkaufMengenbuchung.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create",
                    "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean EinkaufMengenService einkaufMengenService(EinkaufBedarfRepository bedarfRepository,
                EinkaufMengenbuchungRepository buchungRepository) {
            return new EinkaufMengenService(bedarfRepository, buchungRepository);
        }
    }
}
