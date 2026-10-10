package org.example.kalkulationsprogramm.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
 * Erzeugt wird die Basis mit {@code scripts/basis-schema/erzeugen.sh}.
 */
@Configuration
public class FlywayStartSetupConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartSetupConfig.class);

    static final String BASIS_ORT = "classpath:db/basis";
    /** Eigene Historientabelle, damit sich Basis und Migrationen nicht in die Quere kommen. */
    static final String BASIS_HISTORIE = "flyway_basis_history";

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (istLeer(flyway.getConfiguration().getDataSource())) {
                log.info("Leere Datenbank erkannt - spiele das Basis-Schema fuer die Neuinstallation ein.");
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

    /** True, wenn das aktuelle Schema noch keine einzige Tabelle hat. */
    static boolean istLeer(DataSource dataSource) {
        try (Connection verbindung = dataSource.getConnection();
                Statement abfrage = verbindung.createStatement();
                ResultSet ergebnis = abfrage.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            return ergebnis.next() && ergebnis.getLong(1) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Konnte nicht pruefen, ob die Datenbank leer ist", e);
        }
    }
}
