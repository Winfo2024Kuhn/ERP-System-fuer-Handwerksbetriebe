package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandversuch;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandAnnahmeereignis;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAnnahmeereignisConsumer;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandWorker;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EinkaufOutboxParallelTest.Config.class)
class EinkaufOutboxParallelTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_outbox_dummy").withUsername("test").withPassword("test");

    @jakarta.annotation.Resource EinkaufVersandauftragRepository repository;
    @jakarta.annotation.Resource EinkaufVersandWorker worker;
    @jakarta.annotation.Resource KontoMailTransport transport;
    @jakarta.annotation.Resource JdbcTemplate jdbcTemplate;
    @jakarta.annotation.Resource EinkaufOutboxService outbox;
    @jakarta.annotation.Resource org.example.kalkulationsprogramm.repository.EinkaufVersandAnnahmeereignisRepository annahmeereignisse;

    @Test
    void smtpAnnahmePersistiertFachereignisAtomarMitAngenommenStatus() {
        var auftrag = repository.saveAndFlush(new EinkaufVersandauftrag("ATOMIC_TEST", 19L, 4L, 8L,
                "EINKAUF", java.util.UUID.randomUUID(), "payload-accepted", "mime-accepted", "freigabe-hash",
                "{}".getBytes(), "MIME".getBytes(), "<accepted@erp.local>", 1L));
        outbox.beanspruche(auftrag.getId());

        outbox.abgeschlossen(auftrag.getId(), new MailTransportDto.Versandergebnis(
                MailTransportDto.Status.ANGENOMMEN, "<accepted@erp.local>", null, "MIME".getBytes()));

        assertEquals(EinkaufVersandauftrag.Status.ANGENOMMEN,
                repository.findById(auftrag.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from einkauf_versandannahmeereignis where versandauftrag_id = ? and verarbeitet_am is null",
                Integer.class, auftrag.getId()));
    }

    @Test
    void listenerFehlerRolltFachschreibvorgangZurueckUndNeustartKannDasselbeEreignisVerarbeiten() {
        var auftrag = repository.saveAndFlush(new EinkaufVersandauftrag("RECOVERY_TEST", 19L, 4L, 8L,
                "EINKAUF", java.util.UUID.randomUUID(), "payload-recovery", "mime-recovery", "freigabe-hash",
                "{}".getBytes(), "MIME".getBytes(), "<recovery@erp.local>", 1L));
        outbox.beanspruche(auftrag.getId());
        outbox.abgeschlossen(auftrag.getId(), new MailTransportDto.Versandergebnis(
                MailTransportDto.Status.ANGENOMMEN, "<recovery@erp.local>", null, "MIME".getBytes()));
        var anzahlAufrufe = new AtomicInteger();
        var eventKey = new java.util.concurrent.atomic.AtomicReference<String>();
        EinkaufAnnahmeereignisConsumer faelltEinmalAus = consumer(Set.of("RECOVERY_TEST"), ereignis -> {
            anzahlAufrufe.incrementAndGet();
            eventKey.set(ereignis.ereignisSchluessel().toString());
            jdbcTemplate.update("insert into einkauf_annahme_consumer_test (event_key) values (?)", eventKey.get());
            throw new IllegalStateException("simulierter Listener-Ausfall vor Commit");
        });

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> outbox.verarbeiteOffeneAnnahmeereignisse(faelltEinmalAus, 1));

        assertNull(annahmeereignisse.findByVersandauftragId(auftrag.getId()).orElseThrow().getVerarbeitetAm());
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from einkauf_annahme_consumer_test where event_key = ?",
                Integer.class, eventKey.get()));
        int wiederverarbeitet = outbox.verarbeiteOffeneAnnahmeereignisse(consumer(Set.of("RECOVERY_TEST"), ereignis -> {
            assertEquals(eventKey.get(), ereignis.ereignisSchluessel().toString());
            jdbcTemplate.update("insert into einkauf_annahme_consumer_test (event_key) values (?)", eventKey.get());
        }), 1);

        assertEquals(1, wiederverarbeitet);
        assertEquals(0, outbox.verarbeiteOffeneAnnahmeereignisse(consumer(Set.of("RECOVERY_TEST"), ereignis -> {
            throw new AssertionError("Bereits verarbeitetes Ereignis darf nicht erneut zugestellt werden");
        }), 1));
        assertNotNull(annahmeereignisse.findByVersandauftragId(auftrag.getId()).orElseThrow().getVerarbeitetAm());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from einkauf_annahme_consumer_test where event_key = ?",
                Integer.class, eventKey.get()));
        assertEquals(1, anzahlAufrufe.get());
        assertEquals(EinkaufVersandauftrag.Status.ANGENOMMEN,
                repository.findById(auftrag.getId()).orElseThrow().getStatus());
        verify(transport, never()).sendenVorbereitet(any(), any());
    }

    @Test
    void manuelleAnnahmeKlaerungErzeugtEbenfallsDauerhaftesEreignis() {
        var auftrag = new EinkaufVersandauftrag("MANUAL_TEST", 19L, 4L, 8L,
                "EINKAUF", java.util.UUID.randomUUID(), "payload-manual", "mime-manual", "freigabe-hash",
                "{}".getBytes(), "MIME".getBytes(), "<manual@erp.local>", 1L);
        auftrag.starte(1L);
        auftrag.unklar("SMTP_ANTWORT_UNKLAR");
        auftrag = repository.saveAndFlush(auftrag);

        outbox.klaeren(auftrag.getId(), new EinkaufVersandDto.Klaerung(auftrag.getVersion(),
                EinkaufVersandDto.Entscheidung.BEREITS_ANGENOMMEN, "im Dummy-Testpostfach bestätigt"), 2L);

        assertEquals(EinkaufVersandauftrag.Status.ANGENOMMEN,
                repository.findById(auftrag.getId()).orElseThrow().getStatus());
        assertNull(annahmeereignisse.findByVersandauftragId(auftrag.getId()).orElseThrow().getVerarbeitetAm());
    }

    @Test
    void consumerKannNichtZustaendigeBestellereignisseNichtQuittieren() {
        var bestellung = repository.saveAndFlush(new EinkaufVersandauftrag("BESTELLUNG", 20L, 5L, 8L,
                "EINKAUF", java.util.UUID.randomUUID(), "payload-bestellung", "mime-bestellung", "freigabe-hash",
                "{}".getBytes(), "MIME".getBytes(), "<order@erp.local>", 1L));
        outbox.beanspruche(bestellung.getId());
        outbox.abgeschlossen(bestellung.getId(), new MailTransportDto.Versandergebnis(
                MailTransportDto.Status.ANGENOMMEN, "<order@erp.local>", null, "MIME".getBytes()));

        assertEquals(0, outbox.verarbeiteOffeneAnnahmeereignisse(consumer(ignored -> {
            throw new AssertionError("Anfrage-Consumer darf Bestellereignisse nicht erhalten");
        }), 1));
        assertNull(annahmeereignisse.findByVersandauftragId(bestellung.getId()).orElseThrow().getVerarbeitetAm());
    }

    private EinkaufAnnahmeereignisConsumer consumer(Consumer<EinkaufVersandDto.EinkaufVersandAngenommen> action) {
        return consumer(Set.of("ANFRAGE"), action);
    }

    private EinkaufAnnahmeereignisConsumer consumer(Set<String> supportedTypes,
            Consumer<EinkaufVersandDto.EinkaufVersandAngenommen> action) {
        return new EinkaufAnnahmeereignisConsumer() {
            @Override public Set<String> unterstuetzteVorgangstypen() { return supportedTypes; }
            @Override public void verarbeite(EinkaufVersandDto.EinkaufVersandAngenommen ereignis) { action.accept(ereignis); }
        };
    }

    @Test
    void zweiWorkerStartenFuerDieselbeOutboxMailNurEinenSmtpVersuch() throws Exception {
        byte[] mime = "MIME-SNAPSHOT".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var auftrag = repository.saveAndFlush(new EinkaufVersandauftrag("ANFRAGE", 19L, 4L, 8L,
                "EINKAUF", java.util.UUID.randomUUID(), "payload-hash", "mime-hash", "freigabe-hash",
                "{}".getBytes(), mime, "<outbox-test@erp.local>", 1L));
        when(transport.sendenVorbereitet(isNull(), argThat(actual -> java.util.Arrays.equals(actual, mime)))).thenReturn(new MailTransportDto.Versandergebnis(
                MailTransportDto.Status.UNKLAR, "<outbox-test@erp.local>", "SMTP_ANTWORT_UNKLAR", mime));

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) pool.submit(() -> {
            try { start.await(); worker.verarbeite(auftrag.getId()); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { done.countDown(); }
        });
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        verify(transport, times(1)).sendenVorbereitet(isNull(), argThat(actual -> java.util.Arrays.equals(actual, mime)));
        assertEquals(EinkaufVersandauftrag.Status.UNKLAR,
                repository.findById(auftrag.getId()).orElseThrow().getStatus());
    }

    @Configuration
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository",
            includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE, classes = {EinkaufVersandauftragRepository.class,
                            org.example.kalkulationsprogramm.repository.EinkaufVersandAnnahmeereignisRepository.class}))
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            try (var connection = ds.getConnection()) {
                var script = new ClassPathResource("db/migration/V387__einkauf_versand_outbox.sql");
                ScriptUtils.executeSqlScript(connection, script);
                ScriptUtils.executeSqlScript(connection, script);
                try (var statement = connection.createStatement()) {
                    statement.execute("create table if not exists einkauf_annahme_consumer_test (event_key char(36) primary key)");
                }
            }
            return ds;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
            factory.setManagedTypes(PersistenceManagedTypes.of(EinkaufVersandauftrag.class.getName(), EinkaufVersandversuch.class.getName(),
                    EinkaufVersandAnnahmeereignis.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MailkontoService mailkontoService() { return mock(MailkontoService.class); }
        @Bean KontoMailTransport kontoMailTransport() { return mock(KontoMailTransport.class); }
        @Bean SentMailArchiver sentMailArchiver() { return mock(SentMailArchiver.class); }
        @Bean LocalTestMailPolicy localTestMailPolicy() { return mock(LocalTestMailPolicy.class); }
        @Bean EinkaufOutboxService einkaufOutboxService(EinkaufVersandauftragRepository repository,
                org.example.kalkulationsprogramm.repository.EinkaufVersandAnnahmeereignisRepository annahmeereignisse,
                MailkontoService mailkontoService, LocalTestMailPolicy policy, KontoMailTransport transport,
                ObjectMapper mapper, PlatformTransactionManager tm) {
            return new EinkaufOutboxService(repository, annahmeereignisse, mailkontoService, policy, transport, mapper, tm);
        }
        @Bean EinkaufVersandWorker worker(EinkaufOutboxService outbox, MailkontoService konten,
                KontoMailTransport transport, SentMailArchiver archiver, LocalTestMailPolicy policy) {
            return new EinkaufVersandWorker(outbox, konten, transport, archiver, policy);
        }
    }
}
