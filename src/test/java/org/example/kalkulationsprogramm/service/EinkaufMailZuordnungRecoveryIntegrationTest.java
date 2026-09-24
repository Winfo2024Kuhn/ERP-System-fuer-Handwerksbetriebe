package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVergleichService;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufImportZuordnungsService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EinkaufMailZuordnungRecoveryIntegrationTest.Config.class)
class EinkaufMailZuordnungRecoveryIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_mail_recovery_dummy").withUsername("test").withPassword("test");

    @Autowired private EmailRepository emails;
    @Autowired private EinkaufMailZuordnungRepository links;
    @Autowired private EinkaufImportZuordnungsService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher events;

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @Autowired private EinkaufKommunikationVorschauRepository previews;
    @Autowired private EinkaufsanfrageRepository requests;
    @Autowired private EinkaufAngebotRepository offers;
    @Autowired private AngebotVersionRepository versions;
    @Autowired private AnfrageLieferantRepository participations;
    @Autowired private AnfrageRevisionRepository revisions;
    @Autowired private EinkaufVersandauftragRepository dispatches;
    @Autowired private EinkaufVersandAnnahmeereignisRepository acceptances;

    @Test
    void zweiGleichzeitigeFreigabenMitVerschiedenenSchluesselnErzeugenNurEinenVersand() throws Exception {
        // The outbox uses Flyway LONGBLOB columns rather than Hibernate's generic binary DDL.
        try (var connection = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V387__einkauf_versand_outbox.sql"));
        }
        var transaction = new TransactionTemplate(transactionManager);
        long[] ids = transaction.execute(tx -> {
            var request = new Einkaufsanfrage("PA-RACE-DUMMY", 1L, UUID.randomUUID(), "d".repeat(64));
            entityManager.persist(request);
            var revision = new AnfrageRevision(request, 1, null, null, UUID.randomUUID(), "e".repeat(64));
            entityManager.persist(revision); request.setAktuelleRevision(revision);
            var contact = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(
                    1L, 1L, "Dummy", "test@example.com", "Max Mustermann", null, null);
            var supplier = new AnfrageLieferant(revision, contact); entityManager.persist(supplier);
            entityManager.flush();
            return new long[] {request.getId(), supplier.getId(), revision.getId()};
        });
        var templates = mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVorlagenService.class);
        org.mockito.Mockito.when(templates.rendern(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert(1L, 1, "Dummy", "Dummy", "hash"));
        var pdf = mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfService.class);
        byte[] bytes = "dummy-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.mockito.Mockito.when(pdf.erzeugen(org.mockito.ArgumentMatchers.any())).thenReturn(bytes);
        var files = mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService.class);
        org.mockito.Mockito.when(files.speicherePdfSnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.PdfSnapshotDto(1L, "dummy", bytes.length));
        org.mockito.Mockito.when(files.ladePdfSnapshotBytes(1L)).thenReturn(bytes);
        var transport = mock(org.example.kalkulationsprogramm.service.mail.KontoMailTransport.class);
        org.mockito.Mockito.when(transport.vorbereiten(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(bytes);
        var outbox = new org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService(dispatches, acceptances,
                mock(org.example.kalkulationsprogramm.service.mail.MailkontoService.class),
                mock(org.example.kalkulationsprogramm.config.LocalTestMailPolicy.class), transport, new ObjectMapper(), transactionManager);
        var worker = mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandWorker.class);
        var communication = new org.example.kalkulationsprogramm.service.einkauf.EinkaufKommunikationService(requests,
                revisions, participations, emails, links, previews,
                mock(org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository.class),
                mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class),
                templates, pdf, files, outbox, worker,
                mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufMailantwortVersandListener.class), new ObjectMapper());
        var first = transaction.execute(tx -> communication.vorschau(ids[0], ids[1], 1L));
        var second = transaction.execute(tx -> communication.vorschau(ids[0], ids[1], 1L));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var preview : List.of(first, second)) {
                jobs.add(executor.submit(() -> {
                    try {
                        return transaction.execute(tx -> {
                            // Both transactions establish a MySQL REPEATABLE READ snapshot before competing.
                            requests.count();
                            try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                            catch (Exception ex) { throw new IllegalStateException(ex); }
                            communication.senden(ids[0], ids[1], new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Freigabe(
                                    ids[2], preview.vorschauHash(), UUID.randomUUID()), 1L);
                            return true;
                        });
                    } catch (IllegalStateException ex) {
                        assertTrue(ex.getMessage().contains("Versandauftrag"), ex.getMessage());
                        return false;
                    }
                }));
            }
            int successful = 0;
            for (var job : jobs) if (job.get(20, java.util.concurrent.TimeUnit.SECONDS)) successful++;
            assertEquals(1, successful);
        }
        assertEquals(1, dispatches.findAll().stream().filter(d -> ids[0] == d.getVorgangId()).count());
        org.mockito.Mockito.verify(worker, org.mockito.Mockito.times(1)).dispatchNachCommit(org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(transport, org.mockito.Mockito.times(1)).vorbereiten(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        // No SMTP/IMAP operation is invoked by either approval.
        org.mockito.Mockito.verifyNoMoreInteractions(transport);
    }

    @Test
    void gespeicherteVorschauBleibtNachNeuerTransaktionExaktGebunden() {
        String token = "a".repeat(64);
        Instant now = Instant.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> previews.saveAndFlush(
                new EinkaufKommunikationVorschau(token, 11L, 12L, 13L, 14L, 2L,
                        "Anfrage", "<p>Freigegeben</p>", "test@example.com", List.of(21L, 22L),
                        31L, "b".repeat(64), "c".repeat(64), now, now.plusSeconds(3600))));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var stored = previews.findByFreigabeTokenAndAnfrageIdAndBeteiligungId(token, 11L, 12L).orElseThrow();
            assertEquals(31L, stored.getPdfDateiId());
            assertEquals("b".repeat(64), stored.getPdfSha256());
            assertEquals(List.of(21L, 22L), stored.getAnlageVersionIds());
            assertEquals("<p>Freigegeben</p>", stored.getHtmlBody());
            assertTrue(previews.findByFreigabeTokenAndAnfrageIdAndBeteiligungId(token, 99L, 12L).isEmpty());
            assertTrue(previews.findByFreigabeTokenAndAnfrageIdAndBeteiligungId(token, 11L, 99L).isEmpty());
        });
    }

    @Test
    void vergleichLaedtZwoelfLieferantenMitBegrenzterAbfragezahlUndNurNeuestenVersionen() {
        var transaction = new TransactionTemplate(transactionManager);
        Long requestId = transaction.execute(tx -> {
            var request = new Einkaufsanfrage("PA-2026-DUMMY", 1L, UUID.randomUUID(), "a".repeat(64));
            entityManager.persist(request);
            var revision = new AnfrageRevision(request, 1, null, null, UUID.randomUUID(), "b".repeat(64));
            entityManager.persist(revision); request.setAktuelleRevision(revision);
            var basis = new Mengenbasis(BigDecimal.ONE, Einheit.STUECK, BigDecimal.ONE, null, null, null);
            var snapshot = new PositionSnapshot(Positionsart.ARTIKEL, null, null, null, null, "Dummy-Profil",
                    null, null, basis, null, null, null, null, null, List.of(), List.of());
            var demand = new EinkaufBedarf(snapshot, new Liefergruppe("Musterstraße 1", null, null, "Werkstatt"), null, null, false);
            entityManager.persist(demand);
            var position = new AnfragePosition(revision, snapshot, BigDecimal.ONE);
            revision.addPosition(position); entityManager.persist(position);
            var origin = new AnfrageHerkunft(position, demand, 0, BigDecimal.ONE);
            position.addHerkunft(origin); entityManager.persist(origin);
            for (long i = 1; i <= 12; i++) {
                var contact = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(
                        i, i, "Dummy-Lieferant", "test@example.com", "Max Mustermann", null, null);
                var supplier = new AnfrageLieferant(revision, contact); entityManager.persist(supplier);
                var offer = new EinkaufAngebot(supplier); entityManager.persist(offer);
                for (int number = 1; number <= 2; number++) {
                    var version = new AngebotVersion(offer, revision, number, "DUMMY", null, null, "EUR", null, null, null, null, null);
                    version.bestaetigen(1L); entityManager.persist(version);
                    var offered = new AngebotPosition(version, position, "P1", "Dummy-Profil", basis,
                            null, null, null, List.of(), List.of());
                    version.addPosition(offered); entityManager.persist(offered);
                    var cost = new AngebotKostenbestandteil(version, offered, "M", "MATERIAL", BigDecimal.valueOf(number * 10),
                            "STUECK", BigDecimal.ONE, null, false, false, "Dummy-Angebot");
                    version.addKosten(cost); entityManager.persist(cost);
                }
            }
            entityManager.flush();
            return request.getId();
        });
        transaction.executeWithoutResult(tx -> {
            entityManager.clear();
            var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true); statistics.clear();
            var offerService = new EinkaufAngebotService(offers, versions, participations, revisions, emails,
                    mock(EinkaufDateiRepository.class), mock(EmailAttachmentRepository.class), mock(LieferantDokumentRepository.class), links);
            var comparison = new EinkaufVergleichService(requests, offers, versions, offerService)
                    .vergleiche(requestId, LocalDate.of(2026, 9, 23));
            assertEquals(12, comparison.angebote().size());
            assertTrue(comparison.angebote().stream().allMatch(a -> new BigDecimal("20.00").equals(a.nettoGesamt())));
            long queries = statistics.getPrepareStatementCount();
            statistics.setStatisticsEnabled(false);
            assertTrue(queries <= 10, "Batchvergleich benötigte " + queries + " SQL-Abfragen");
        });
    }

    @Test
    void committedImportEventWirdNachCommitInEigenerTransaktionDauerhaftZugeordnet() {
        Long id = new TransactionTemplate(transactionManager).execute(tx -> {
            Email email = emails.saveAndFlush(email("<after-commit@example.test>"));
            events.publishEvent(new EmailImportService.EinkaufEmailImportiert(email.getId()));
            return email.getId();
        });

        assertEquals("PRUEFEN", links.findByEmailId(id).orElseThrow().getStatus());
    }

    @Test
    void nachNeustartFindetRecoveryImportmailDieOhneListenercommitPersistiertBlieb() {
        Email email = emails.saveAndFlush(email("<restart-recovery@example.test>"));

        assertEquals(1, service.verarbeiteOffeneImportZuordnungen(50));

        assertEquals("PRUEFEN", links.findByEmailId(email.getId()).orElseThrow().getStatus());
        assertEquals(0, service.verarbeiteOffeneImportZuordnungen(50));
    }

    @Test
    void zurueckgerollterImportwirdNichtVorzeitigZugeordnetUndIstNichtSichtbar() {
        Long id = new TransactionTemplate(transactionManager).execute(tx -> {
            Email email = emails.saveAndFlush(email("<rollback@example.test>"));
            events.publishEvent(new EmailImportService.EinkaufEmailImportiert(email.getId()));
            tx.setRollbackOnly();
            return email.getId();
        });

        assertFalse(emails.existsById(id));
        assertFalse(links.findByEmailId(id).isPresent());
    }

    private static Email email(String messageId) {
        Email email = new Email();
        email.setMessageId(messageId);
        email.setKontoId("EINKAUF");
        email.setDirection(EmailDirection.IN);
        return email;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository")
    static class Config {
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("org.example.kalkulationsprogramm.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean EinkaufImportZuordnungsService service(EmailRepository emails, EinkaufMailZuordnungRepository links,
                ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
            return new org.example.kalkulationsprogramm.service.einkauf.EinkaufAntwortZuordnungService(emails,
                    mock(org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository.class), links,
                    mock(org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository.class),
                    mock(org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository.class),
                    mock(EinkaufAuditService.class), objectMapper,
                    mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class), transactionManager);
        }
        @Bean org.example.kalkulationsprogramm.service.einkauf.EinkaufImportMailListener listener(
                EinkaufImportZuordnungsService consumer) {
            return new org.example.kalkulationsprogramm.service.einkauf.EinkaufImportMailListener(consumer);
        }
    }
}
