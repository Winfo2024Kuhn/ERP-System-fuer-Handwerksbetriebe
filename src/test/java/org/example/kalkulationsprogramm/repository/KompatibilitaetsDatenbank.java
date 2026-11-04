package org.example.kalkulationsprogramm.repository;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Verbindet einen Kompatibilitaetstest mit der echten Wegwerf-PostgreSQL aus
 * {@code -Dkompat.db.url}: Basis-Schema + Flyway-Migrationen wie in der
 * Produktion, danach {@code ddl-auto=validate}.
 *
 * <p>Die Testklassen brauchen dazu zusaetzlich die Flyway-Properties aus
 * {@link DatenbankKompatibilitaetTest} (in der Annotation, Arrays sind dort keine Konstanten),
 * {@code @AutoConfigureTestDatabase(replace = NONE)} und
 * {@code @Import(FlywayStartSetupConfig.class)}.
 */
final class KompatibilitaetsDatenbank {

    private KompatibilitaetsDatenbank() {
    }

    static void eintragen(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("kompat.db.url"));
        // Test-application.properties stellt fest auf H2 - hier die echte Datenbank
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Wie application.properties der App (die Test-Properties ersetzen sie)
        r.add("spring.flyway.locations", () -> "classpath:db/postgresql/migration");
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET client_encoding TO 'UTF8'");
        kleinerVerbindungsPool(r);
        r.add("spring.datasource.username", () -> System.getProperty("kompat.db.user", "kompat"));
        r.add("spring.datasource.password", () -> System.getProperty("kompat.db.pass", "kompat"));
    }

    /**
     * Jede Testklasse mit eigener Konfiguration bekommt einen eigenen, im Kontext-Cache gehaltenen
     * Spring-Kontext samt Verbindungspool. Mit Hikaris Standard (10 Verbindungen, alle offen
     * gehalten) erschöpfen gut ein Dutzend Klassen die 100 Verbindungen der CI-Datenbank
     * („remaining connection slots are reserved“). Drei reichen auch für die Sperr-Rennen
     * (Test-Thread + ein Neben-Thread).
     */
    static void kleinerVerbindungsPool(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        r.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }
}
