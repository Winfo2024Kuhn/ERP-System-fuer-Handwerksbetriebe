package org.example.kalkulationsprogramm.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellfreigabeDto.Freigabe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EinkaufBestellungParallelTest.Config.class)
class EinkaufBestellungParallelTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_bestellung_dummy").withUsername("test").withPassword("test");

    @jakarta.annotation.Resource EinkaufBestellungRepository orders;
    @jakarta.annotation.Resource BestellungRevisionRepository revisions;
    @jakarta.annotation.Resource EinkaufVersandauftragRepository outbox;
    @jakarta.annotation.Resource EinkaufKommunikationVorschauRepository previews;
    @jakarta.annotation.Resource EinkaufBestellfreigabeService service;
    @jakarta.annotation.Resource EinkaufDateiService files;
    @jakarta.annotation.Resource PlatformTransactionManager transactionManager;
    @jakarta.annotation.Resource org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void verschiedeneFreigabeschluesselErzeugenFuerDieselbeRevisionGenauEinenOutboxauftrag() throws Exception {
        var recipient = new Snapshot(7L, 8L, "Muster Stahl", "test@example.com", "Testkontakt", "Herr", null);
        var order = orders.saveAndFlush(new EinkaufBestellung("B-DUMMY-1", 7L, null, null, recipient,
                UUID.randomUUID(), "order-hash", 9L));
        var revision = new BestellungRevision(order, 1, Map.of("typ", "DIREKT"), "a".repeat(64), 9L);
        var position = new PositionSnapshot(Positionsart.ARTIKEL, 21L, "A-21", null, null, "Blech",
                null, null, null, null, null, null, null, null, List.of(), List.of());
        revision.addPosition(new BestellungPosition(revision, position, java.math.BigDecimal.ONE,
                new java.math.BigDecimal("12.50"), "EUR", List.of(), Map.of()));
        order.addRevision(revision);
        var persistedOrder = orders.saveAndFlush(order);
        var persistedRevision = revisions.findFirstByBestellung_IdOrderByNummerDesc(persistedOrder.getId()).orElseThrow();

        String token = "b".repeat(64);
        String subject = "Bestellung B-DUMMY-1";
        String html = "<p>Bitte bestätigen</p>";
        byte[] pdf = "dummy-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        String pdfHash = sha256(pdf);
        String previewHash = sha256(token + "|" + persistedOrder.getId() + "|" + persistedOrder.getVersion() + "|"
                + persistedRevision.getId() + "|" + subject + "|" + html + "|55|" + pdfHash + "|[]");
        var preview = new EinkaufKommunikationVorschau(token, persistedOrder.getId(), persistedOrder.getId(),
                persistedRevision.getId(), 4L, 1L, subject, html, recipient.email(), List.of(), 55L, pdfHash,
                previewHash, Instant.now(), Instant.now().plusSeconds(3600));
        when(previews.findByFreigabeTokenAndAnfrageIdAndBeteiligungId(token, persistedOrder.getId(), persistedOrder.getId()))
                .thenReturn(Optional.of(preview));
        when(files.ladePdfSnapshotBytes(55L)).thenReturn(pdf);
        when(files.ladeVersandanlagen(List.of())).thenReturn(List.of());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID key : List.of(UUID.randomUUID(), UUID.randomUUID())) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    new TransactionTemplate(transactionManager).execute(status -> service.freigeben(persistedOrder.getId(),
                            new Freigabe(persistedOrder.getVersion(), token, key), 9L));
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException expectedLoser) {
                    return false;
                }
            }));
        }
        start.countDown();
        List<Boolean> completed = List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, completed.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, completed.stream().filter(value -> !value).count());
        assertEquals(1, outbox.findAllByStatus(EinkaufVersandauftrag.Status.VORBEREITET).stream()
                .filter(a -> a.getVorgangId().equals(persistedOrder.getId()) && a.getRevisionId().equals(persistedRevision.getId())).count());
    }

    @Test
    void migrationenV390V392V393SindAufMySqlAnwendbarUndEinkaufsmodelleValidieren() {
        assertTrue(MYSQL.isRunning());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='lieferanten_artikel_preise' and column_name='komponenten_hash'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='einkauf_bestellung_revision' and column_name='versand_id'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='lieferant_dokument' and column_name='einkauf_bestellung_id'", Integer.class));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String sha256(String value) throws Exception { return sha256(value.getBytes(StandardCharsets.UTF_8)); }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository", includeFilters =
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {EinkaufBestellungRepository.class,
                    BestellungRevisionRepository.class, EinkaufVersandauftragRepository.class}))
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("create table lieferanten_artikel_preise (id bigint not null auto_increment primary key, artikel_id bigint, lieferant_id bigint, aktuell bit not null default 1) engine=InnoDB");
                statement.execute("create table einkauf_angebot_version (id bigint not null primary key) engine=InnoDB");
                statement.execute("create table einkaufsanfrage_revision (id bigint not null primary key) engine=InnoDB");
                statement.execute("create table einkauf_bedarf (id bigint not null primary key) engine=InnoDB");
                statement.execute("create table lieferant_dokument (id bigint not null primary key) engine=InnoDB");
                statement.execute("create table einkaufsanfrage_lieferant (id bigint not null primary key) engine=InnoDB");
                statement.execute("create table email (id bigint not null primary key, konto_id varchar(30), direction varchar(10)) engine=InnoDB");
            }
            try (var connection = source.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V390__einkauf_preishistorie_quellen.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V387__einkauf_versand_outbox.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V388__einkauf_kommunikation.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V392__einkauf_bestellungen.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V393__einkauf_lieferungen_chargen.sql"));
            }
            return source;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
            factory.setManagedTypes(org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(EinkaufBestellung.class.getName(), BestellungRevision.class.getName(),
                    BestellungPosition.class.getName(), BestellungHerkunft.class.getName(), EinkaufLieferung.class.getName(),
                    LieferungPosition.class.getName(), EinkaufCharge.class.getName(), BestellBestaetigung.class.getName(),
                    EinkaufVersandauftrag.class.getName(), EinkaufVersandversuch.class.getName(),
                    EinkaufVersandAnnahmeereignis.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(DataSource dataSource) { return new org.springframework.jdbc.core.JdbcTemplate(dataSource); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean EinkaufKommunikationVorschauRepository previews() { return mock(EinkaufKommunikationVorschauRepository.class); }
        @Bean EinkaufVorlagenService templates() { return mock(EinkaufVorlagenService.class); }
        @Bean EinkaufPdfService pdf() { return mock(EinkaufPdfService.class); }
        @Bean EinkaufDateiService files() { return mock(EinkaufDateiService.class); }
        @Bean EinkaufOutboxService outboxService(EinkaufVersandauftragRepository repo) {
            EinkaufOutboxService mocked = mock(EinkaufOutboxService.class);
            when(mocked.einreihen(any(), any(), any())).thenAnswer(call -> {
                VersandSnapshot snapshot = call.getArgument(0);
                UUID key = call.getArgument(1);
                var saved = repo.saveAndFlush(new EinkaufVersandauftrag(snapshot.typ(), snapshot.vorgangId(),
                        snapshot.revisionId(), snapshot.beteiligungId(), "EINKAUF", key, "payload", "mime",
                        snapshot.freigabeHash(), "{}".getBytes(StandardCharsets.UTF_8), "MIME".getBytes(StandardCharsets.UTF_8),
                        "<dummy@erp.local>", 9L));
                return new VersandDto(saved.getId(), saved.getVersion(), saved.getTyp(), saved.getVorgangId(),
                        saved.getRevisionId(), saved.getStatus().name(), null, saved.getErstelltAm(), null, false, saved.getMessageId());
            });
            return mocked;
        }
        @Bean EinkaufVersandWorker worker() { return mock(EinkaufVersandWorker.class); }
        @Bean EinkaufMengenService amounts() { return mock(EinkaufMengenService.class); }
        @Bean EinkaufBedarfRepository needs() { return mock(EinkaufBedarfRepository.class); }
        @Bean LieferantDokumentRepository documents() { return mock(LieferantDokumentRepository.class); }
        @Bean EinkaufAuditService audit() { return mock(EinkaufAuditService.class); }
        @Bean AngebotVersionRepository offers() { return mock(AngebotVersionRepository.class); }
        @Bean EinkaufBestellfreigabeService service(EinkaufBestellungRepository orders,
                BestellungRevisionRepository revisions, AngebotVersionRepository offers,
                EinkaufKommunikationVorschauRepository previews, EinkaufVorlagenService templates,
                EinkaufPdfService pdf, EinkaufDateiService files, EinkaufOutboxService outbox,
                EinkaufVersandWorker worker, EinkaufVersandauftragRepository outboxRepo,
                EinkaufMengenService amounts, EinkaufBedarfRepository needs,
                LieferantDokumentRepository documents, EinkaufAuditService audit, ObjectMapper mapper) {
            return new EinkaufBestellfreigabeService(orders, revisions, offers, previews, templates, pdf, files,
                    outbox, worker, outboxRepo, amounts, needs, documents, audit, mapper);
        }
    }
}
