package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.einkauf.EmailImportIdentitaet;
import org.example.kalkulationsprogramm.repository.EmailImportIdentitaetRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EmailKontoImportTest.TestConfig.class)
class EmailKontoImportTest {
    @Container
    private static final MySQLContainer<?> JPA_MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("email_import_jpa_dummy")
            .withUsername("test")
            .withPassword("test");

    @Container
    private static final MySQLContainer<?> MIGRATION_MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("email_import_migration_dummy")
            .withUsername("test")
            .withPassword("test");

    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailImportIdentitaetRepository identitaetRepository;

    @Test
    void jpaPersistiertGleicheMessageIdGetrenntJeKontoUndBindetUidIdentitaet() {
        Email haupt = email("<jpa-identitaet@example.test>", "HAUPT");
        Email einkauf = email("<jpa-identitaet@example.test>", "EINKAUF");
        haupt = emailRepository.saveAndFlush(haupt);
        final Email einkaufMail = emailRepository.saveAndFlush(einkauf);

        assertThat(emailRepository.findByKontoIdAndMessageId("HAUPT", haupt.getMessageId()).orElseThrow().getId())
                .isEqualTo(haupt.getId());
        assertThat(emailRepository.findByKontoIdAndMessageId("EINKAUF", einkaufMail.getMessageId()).orElseThrow().getId())
                .isEqualTo(einkaufMail.getId());

        identitaetRepository.saveAndFlush(new EmailImportIdentitaet("EINKAUF", "INBOX", 81, 82, einkaufMail));
        assertThat(identitaetRepository.existsByKontoIdAndFolderAndUidValidityAndUid(
                "EINKAUF", "INBOX", 81, 82)).isTrue();
        assertThatThrownBy(() -> identitaetRepository.saveAndFlush(
                new EmailImportIdentitaet("EINKAUF", "INBOX", 81, 82, einkaufMail)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void migrationBackfillsMainAndScopesMessageAndUidIdentityToAccount() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MIGRATION_MYSQL.getJdbcUrl(), MIGRATION_MYSQL.getUsername(), MIGRATION_MYSQL.getPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE email (id BIGINT NOT NULL AUTO_INCREMENT, message_id VARCHAR(512) NOT NULL, "
                        + "PRIMARY KEY (id), UNIQUE KEY idx_email_message_id (message_id), "
                        + "UNIQUE KEY UK_email_message_id (message_id)) ENGINE=InnoDB");
                statement.executeUpdate("INSERT INTO email (message_id) VALUES ('<same@example.test>')");
            }

            executeScript(connection, migrationSql());
            executeScript(connection, migrationSql());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO email (message_id,konto_id) VALUES ('<same@example.test>','EINKAUF')");
                statement.executeUpdate("INSERT INTO email_import_identitaet (konto_id,folder,uidvalidity,uid,email_id) "
                        + "VALUES ('HAUPT','INBOX',11,12,1)");
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO email (message_id,konto_id) "
                        + "VALUES ('<same@example.test>','HAUPT')"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO email_import_identitaet "
                        + "(konto_id,folder,uidvalidity,uid,email_id) VALUES ('HAUPT','INBOX',11,12,2)"))
                        .isInstanceOf(SQLException.class);
                try (var rows = statement.executeQuery("SELECT konto_id FROM email WHERE id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("HAUPT");
                }
            }

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var inserted = new AtomicInteger();
            try (var workers = Executors.newFixedThreadPool(2)) {
                List<java.util.concurrent.Future<?>> attempts = new ArrayList<>();
                for (long emailId : List.of(1L, 2L)) {
                    attempts.add(workers.submit(() -> {
                        ready.countDown();
                        try {
                            assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                            try (Connection concurrent = DriverManager.getConnection(
                                    MIGRATION_MYSQL.getJdbcUrl(), MIGRATION_MYSQL.getUsername(), MIGRATION_MYSQL.getPassword());
                                    Statement statement = concurrent.createStatement()) {
                                statement.executeUpdate("INSERT INTO email_import_identitaet "
                                        + "(konto_id,folder,uidvalidity,uid,email_id) "
                                        + "VALUES ('EINKAUF','INBOX',91,92," + emailId + ")");
                                inserted.incrementAndGet();
                            }
                        } catch (SQLException expectedUniqueKeyRace) {
                            // Die Datenbank entscheidet atomar, welcher gleichzeitige Import gewinnt.
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    }));
                }
                assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                start.countDown();
                attempts.forEach(attempt -> {
                    try { attempt.get(10, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception ex) { throw new AssertionError(ex); }
                });
            }
            assertThat(inserted.get()).isEqualTo(1);
        }
    }

    private Email email(String messageId, String kontoId) {
        Email email = new Email();
        email.setMessageId(messageId);
        email.setKontoId(kontoId);
        email.setDirection(EmailDirection.IN);
        return email;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "org.example.kalkulationsprogramm.repository",
            excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "org\\.example\\.kalkulationsprogramm\\.repository\\.(?!EmailRepository$|EmailImportIdentitaetRepository$).*$"))
    static class TestConfig {
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource(JPA_MYSQL.getJdbcUrl(), JPA_MYSQL.getUsername(), JPA_MYSQL.getPassword());
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
    }

    private String migrationSql() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V384__email_kontobezug_importidentitaet.sql")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        }
    }

    private void executeScript(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String part : splitStatements(sql)) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) current.append(sql.charAt(++i));
                    else quote = 0;
                } else if (c == '\\' && i + 1 < sql.length()) current.append(sql.charAt(++i));
            } else if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == ';') {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
