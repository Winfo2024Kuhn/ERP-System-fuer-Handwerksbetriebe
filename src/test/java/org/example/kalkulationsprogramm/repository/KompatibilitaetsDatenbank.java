package org.example.kalkulationsprogramm.repository;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Verbindet einen Kompatibilitaetstest mit der echten Wegwerf-Datenbank aus
 * {@code -Dkompat.db.url} (MySQL oder PostgreSQL) - genauso wie
 * {@link DatenbankKompatibilitaetTest}: eigene Migrationslinie je Datenbank,
 * Basis-Schema + Flyway, danach {@code ddl-auto=validate}.
 *
 * <p>Die Testklassen brauchen dazu zusaetzlich die Flyway-Properties aus
 * {@link DatenbankKompatibilitaetTest} (in der Annotation, Arrays sind dort keine Konstanten),
 * {@code @AutoConfigureTestDatabase(replace = NONE)} und
 * {@code @Import(FlywayStartSetupConfig.class)}.
 */
final class KompatibilitaetsDatenbank {

    private KompatibilitaetsDatenbank() {
    }

    static boolean istPostgres() {
        return System.getProperty("kompat.db.url", "").startsWith("jdbc:postgresql:");
    }

    static void eintragen(DynamicPropertyRegistry r) {
        String url = System.getProperty("kompat.db.url");
        boolean postgres = istPostgres();
        r.add("spring.datasource.url", () -> url);
        // Test-application.properties stellt fest auf H2 - hier die echte Datenbank
        r.add("spring.datasource.driver-class-name", () -> postgres ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.jpa.database-platform", () -> postgres
                ? "org.hibernate.dialect.PostgreSQLDialect" : "org.hibernate.dialect.MySQLDialect");
        // Je Datenbank die eigene Migrationslinie (wie application-postgres.properties)
        r.add("spring.flyway.locations", () -> postgres ? "classpath:db/postgresql/migration" : "classpath:db/migration");
        r.add("spring.datasource.username", () -> System.getProperty("kompat.db.user", "root"));
        r.add("spring.datasource.password", () -> System.getProperty("kompat.db.pass", ""));
        // "SET NAMES utf8mb4" aus application.properties ist MySQL-Syntax
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SELECT 1");
    }
}
