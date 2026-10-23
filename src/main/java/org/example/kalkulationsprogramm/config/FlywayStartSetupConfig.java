package org.example.kalkulationsprogramm.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Startsetup für Neuinstallationen: Eine LEERE Datenbank bekommt zuerst das
 * Basis-Schema ({@code db/postgresql/basis/V1__basis_schema.sql}), danach
 * laufen die normalen Flyway-Migrationen.
 *
 * <p>Warum: Die Migrationen beginnen erst nach V406, das Grundschema davor
 * steckt komplett in der Basis. Ohne Basis bricht der erste Start auf einer
 * leeren Datenbank an der ersten Migration ab.
 *
 * <p>Bestehende Datenbanken (mindestens eine Tabelle) sind nicht betroffen.
 * Erzeugt wird die Basis mit {@code scripts/basis-schema/postgres_erzeugen.sh}.
 *
 * <p>PostgreSQL ist die einzige unterstuetzte Datenbank. Zeigt die Verbindung
 * auf etwas anderes, bricht der Start ab, bevor Flyway irgendetwas anlegt.
 * (Ohne passenden JDBC-Treiber - z. B. bei einer alten MySQL-Adresse - scheitert
 * der Start meist schon vorher an der Verbindung.)
 */
@Configuration
public class FlywayStartSetupConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartSetupConfig.class);

    static final String BASIS_ORT = "classpath:db/postgresql/basis";
    /** Eigene Historientabelle, damit sich Basis und Migrationen nicht in die Quere kommen. */
    static final String BASIS_HISTORIE = "flyway_basis_history";

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            pruefePostgres(dataSource);
            if (istLeer(dataSource)) {
                log.info("Leere Datenbank erkannt - spiele das Basis-Schema fuer die Neuinstallation ein ({}).", BASIS_ORT);
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .locations(BASIS_ORT)
                        .table(BASIS_HISTORIE)
                        .baselineOnMigrate(false)
                        // Stammdaten (Textvorlagen) enthalten ${...}-Platzhalter der App
                        .placeholderReplacement(false)
                        .load()
                        .migrate();
                log.info("Basis-Schema eingespielt.");
            }
            flyway.migrate();
        };
    }

    /**
     * Bricht den Start ab, wenn die Datenbank kein PostgreSQL ist. Lieber eine
     * klare Meldung als ein halb migriertes Schema.
     */
    static void pruefePostgres(DataSource dataSource) {
        String produkt;
        try (Connection verbindung = dataSource.getConnection()) {
            produkt = verbindung.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankart konnte nicht ermittelt werden", e);
        }
        if (produkt == null || !produkt.toLowerCase(Locale.ROOT).contains("postgres")) {
            throw new IllegalStateException("Das ERP laeuft nur noch mit PostgreSQL, die Verbindung zeigt aber auf \""
                    + produkt + "\". Bitte spring.datasource.url bzw. APP_DB_URL auf die PostgreSQL-Datenbank stellen.");
        }
    }

    /**
     * True, wenn das aktuelle Schema noch keine einzige Tabelle hat. Ueber die
     * JDBC-Metadaten (Schema "public") statt SQL.
     */
    static boolean istLeer(DataSource dataSource) {
        try (Connection verbindung = dataSource.getConnection();
                ResultSet tabellen = verbindung.getMetaData().getTables(
                        verbindung.getCatalog(), verbindung.getSchema(), "%", new String[] { "TABLE" })) {
            return !tabellen.next();
        } catch (SQLException e) {
            throw new IllegalStateException("Konnte nicht pruefen, ob die Datenbank leer ist", e);
        }
    }
}
