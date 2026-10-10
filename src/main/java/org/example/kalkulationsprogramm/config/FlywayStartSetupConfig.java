package org.example.kalkulationsprogramm.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Startsetup für Neuinstallationen: Eine LEERE Datenbank bekommt zuerst das
 * Basis-Schema ({@code db/basis/V1__basis_schema.sql}), danach laufen die
 * normalen Flyway-Migrationen.
 *
 * <p>Warum: Die Migrationen beginnen erst bei V208, das Grundschema davor hat
 * früher Hibernate angelegt. Ohne Basis bricht der erste Start auf einer leeren
 * MySQL an V208 ab ("Table 'email' doesn't exist").
 *
 * <p>Das Basis-Schema bringt die Flyway-Historie aller darin enthaltenen
 * Migrationen mit (Flyway-genaue Checksummen). Flyway behandelt eine so
 * angelegte Datenbank deshalb genau wie eine gewachsene; neue Migrationen
 * laufen normal - auch mit kleinerer Nummer (out-of-order).
 *
 * <p>Bestehende Datenbanken (mindestens eine Tabelle) sind nicht betroffen.
 * Erzeugt wird die Basis mit {@code scripts/basis-schema/erzeugen.sh} (MySQL)
 * bzw. {@code scripts/basis-schema/postgres_erzeugen.sh} (PostgreSQL).
 */
@Configuration
public class FlywayStartSetupConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartSetupConfig.class);

    static final String BASIS_ORT_MYSQL = "classpath:db/basis";
    static final String BASIS_ORT_POSTGRES = "classpath:db/postgresql/basis";
    /** Eigene Historientabelle, damit sich Basis und Migrationen nicht in die Quere kommen. */
    static final String BASIS_HISTORIE = "flyway_basis_history";

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            if (istLeer(dataSource)) {
                String basisOrt = basisOrt(dataSource);
                log.info("Leere Datenbank erkannt - spiele das Basis-Schema fuer die Neuinstallation ein ({}).", basisOrt);
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .locations(basisOrt)
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

    /** PostgreSQL (Kunden) und MySQL (eigener Server) haben je eine eigene Basis. */
    static String basisOrt(DataSource dataSource) {
        return DatenbankArt.istPostgres(dataSource) ? BASIS_ORT_POSTGRES : BASIS_ORT_MYSQL;
    }

    /**
     * True, wenn das aktuelle Schema noch keine einzige Tabelle hat. Ueber die
     * JDBC-Metadaten statt SQL - das funktioniert gleich auf MySQL (Katalog =
     * Datenbank) und PostgreSQL (Schema "public").
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
