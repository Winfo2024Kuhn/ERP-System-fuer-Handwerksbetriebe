package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ueberstehen alle Migrationen einen zweiten Lauf? BACKEND_ARCH.md verlangt idempotente
 * Migrationen - laeuft eine nach einem Abbruch (Nachtupdate, Rueckspielen einer
 * Sicherung) noch einmal, darf sie nicht scheitern und nichts doppelt anlegen.
 *
 * <p>Beim Start hat Flyway das Schema der Wegwerf-Datenbank schon migriert (Basis +
 * alle Migrationen). Eine eigene Historientabelle zwingt Flyway, jede Migration noch
 * einmal auf dem fertigen Schema auszufuehren; danach wird sie wieder entfernt.
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetMigrationenTest {

    private static final Path MIGRATIONEN = Path.of("src", "main", "resources", "db", "postgresql", "migration");
    private static final String ZWEITER_LAUF = "flyway_zweiter_lauf";

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("Jede Migration laeuft auf dem fertigen Schema ein zweites Mal fehlerfrei")
    void zweiterLaufOhneFehler() throws IOException {
        long anzahl;
        try (Stream<Path> dateien = Files.list(MIGRATIONEN)) {
            anzahl = dateien.filter(datei -> datei.getFileName().toString().endsWith(".sql")).count();
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS " + ZWEITER_LAUF);
        Map<String, Long> vorher = zeilenJeTabelle(jdbc);
        try {
            MigrateResult ergebnis = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/postgresql/migration")
                    .table(ZWEITER_LAUF)
                    // Schema ist nicht leer: Baseline unterhalb aller Migrationen, dann laufen alle
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();

            assertThat(ergebnis.success).isTrue();
            assertThat(ergebnis.migrationsExecuted).as("jede Migration noch einmal").isEqualTo((int) anzahl);
            // Nichts doppelt angelegt: z. B. ein INSERT ohne ON CONFLICT faellt hier auf
            assertThat(zeilenJeTabelle(jdbc)).as("Zeilen je Tabelle nach dem zweiten Lauf").isEqualTo(vorher);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + ZWEITER_LAUF);
        }
    }

    /** Zeilenzahl jeder Tabelle des Schemas (ohne Flyways eigene Historientabellen). */
    private static Map<String, Long> zeilenJeTabelle(JdbcTemplate jdbc) {
        Map<String, Long> ergebnis = new TreeMap<>();
        List<String> tabellen = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "
                + "AND table_name NOT LIKE 'flyway%'", String.class);
        for (String tabelle : tabellen) {
            // Name stammt aus information_schema, nicht aus einer Eingabe - trotzdem quoten
            ergebnis.put(tabelle, jdbc.queryForObject(
                    "SELECT count(*) FROM \"" + tabelle.replace("\"", "\"\"") + "\"", Long.class));
        }
        return ergebnis;
    }
}
