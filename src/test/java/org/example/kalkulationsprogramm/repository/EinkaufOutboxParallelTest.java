package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandversuch;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandWorker;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                    type = FilterType.ASSIGNABLE_TYPE, classes = EinkaufVersandauftragRepository.class))
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            try (var connection = ds.getConnection()) {
                var script = new ClassPathResource("db/migration/V387__einkauf_versand_outbox.sql");
                ScriptUtils.executeSqlScript(connection, script);
                ScriptUtils.executeSqlScript(connection, script);
            }
            return ds;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
            factory.setManagedTypes(PersistenceManagedTypes.of(EinkaufVersandauftrag.class.getName(), EinkaufVersandversuch.class.getName()));
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
                MailkontoService mailkontoService, LocalTestMailPolicy policy, KontoMailTransport transport,
                ObjectMapper mapper, PlatformTransactionManager tm, ApplicationEventPublisher events) {
            return new EinkaufOutboxService(repository, mailkontoService, policy, transport, mapper, tm, events);
        }
        @Bean EinkaufVersandWorker worker(EinkaufOutboxService outbox, MailkontoService konten,
                KontoMailTransport transport, SentMailArchiver archiver, LocalTestMailPolicy policy) {
            return new EinkaufVersandWorker(outbox, konten, transport, archiver, policy);
        }
    }
}
