package org.example.kalkulationsprogramm.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EinkaufVorlagenMigrationMysqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("migration_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void seedsPreserveExistingTemplatesAndRemainIdempotentOnMysql() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE email_text_template ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT, dokument_typ VARCHAR(40) NOT NULL, "
                        + "kategorie ENUM('DOKUMENT','MAHNWESEN','WEBSITE','SYSTEM') NULL, "
                        + "name VARCHAR(150) NOT NULL, subject_template VARCHAR(500) NOT NULL, "
                        + "html_body LONGTEXT NOT NULL, aktiv TINYINT(1) NOT NULL DEFAULT 1, "
                        + "created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL, PRIMARY KEY (id), "
                        + "UNIQUE KEY uk_email_text_template_doktyp (dokument_typ)) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                statement.executeUpdate("INSERT INTO email_text_template "
                        + "(dokument_typ,kategorie,name,subject_template,html_body) VALUES "
                        + "('RECHNUNG','DOKUMENT','Rechnung','Benutzerfassung','Rechnungstext'), "
                        + "('EINKAUF_ANFRAGE',NULL,'Eigene Anfrage','Eigener Betreff','Eigener Text')");
            }

            String migration = migrationSql();
            executeScript(connection, migration);
            assertSeedState(connection);
            executeScript(connection, migration);
            assertSeedState(connection);
        }
    }

    private void assertSeedState(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM email_text_template "
                    + "WHERE dokument_typ='EINKAUF_ANFRAGE'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("eine Vorlage je Typ trotz vorhandener Betreiberfassung").isEqualTo(1);
            }
            try (ResultSet rs = statement.executeQuery("SELECT name, subject_template, html_body, standard "
                    + "FROM email_text_template WHERE dokument_typ='EINKAUF_ANFRAGE'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("name")).isEqualTo("Eigene Anfrage");
                assertThat(rs.getString("subject_template")).isEqualTo("Eigener Betreff");
                assertThat(rs.getString("html_body")).isEqualTo("Eigener Text");
                assertThat(rs.getBoolean("standard")).isTrue();
                assertThat(rs.next()).isFalse();
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM email_text_template "
                    + "WHERE dokument_typ LIKE 'EINKAUF\\_%' GROUP BY dokument_typ HAVING COUNT(*) <> 1")) {
                assertThat(rs.next()).as("keine Einkaufsart hat mehrere Vorlagen nach Wiederholung").isFalse();
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM email_text_template_standard")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(8);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM email_text_template t "
                    + "JOIN email_text_template_standard s ON s.template_id=t.id "
                    + "WHERE t.standard=1 AND s.dokument_typ=t.dokument_typ")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(8);
            }
        }
    }

    private String migrationSql() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V385__einkauf_vorlagen_varianten.sql")) {
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
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        current.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                } else if (c == '\\' && i + 1 < sql.length()) {
                    current.append(sql.charAt(++i));
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == ';') {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
