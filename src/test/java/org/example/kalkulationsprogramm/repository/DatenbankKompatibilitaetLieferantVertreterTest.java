package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datenübernahme von V450 auf echtem PostgreSQL: Das alte Feld {@code lieferanten.vertreter}
 * wird zu einem Ansprechpartner mit Aufgabe „Vertreter“, die Spalte verschwindet, ein zweiter
 * Lauf legt nichts doppelt an.
 *
 * <p>Das Schema ist beim Start schon fertig migriert (ohne {@code vertreter}). Der Test legt die
 * alte Spalte wieder an, füllt sie und lässt nur V450 (Kopie in einem eigenen Ordner, eigene
 * Historientabelle) laufen. Aufgeräumt wird in jedem Fall.</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetLieferantVertreterTest {

    private static final String V450 = "V450__lieferant_ansprechpartner.sql";
    private static final String HISTORIE = "flyway_v450_vertreter_test";

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    DataSource dataSource;

    @TempDir
    Path ordner;

    @Test
    @DisplayName("V450 übernimmt den Vertreter getrimmt als Ansprechpartner, löscht die Spalte und legt nichts doppelt an")
    void vertreterWirdAnsprechpartner() throws IOException {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Files.copy(Path.of("src", "main", "resources", "db", "postgresql", "migration", V450), ordner.resolve(V450));
        Long mitVertreter = null;
        Long ohneVertreter = null;
        try {
            jdbc.execute("ALTER TABLE lieferanten ADD COLUMN IF NOT EXISTS vertreter VARCHAR(255)");
            mitVertreter = jdbc.queryForObject("INSERT INTO lieferanten (lieferantenname, vertreter, vorauskasse) "
                    + "VALUES ('Muster Stahl KG V450-Test', '  Max Mustermann ', false) RETURNING id", Long.class);
            ohneVertreter = jdbc.queryForObject("INSERT INTO lieferanten (lieferantenname, vertreter, vorauskasse) "
                    + "VALUES ('Holz Beispiel V450-Test', '   ', false) RETURNING id", Long.class);

            migriereV450();
            migriereV450();

            List<Map<String, Object>> ansprechpartner = jdbc.queryForList(
                    "SELECT nachname, aufgabe, vorname FROM lieferant_ansprechpartner WHERE lieferant_id = ?", mitVertreter);
            assertThat(ansprechpartner).singleElement().satisfies(a -> {
                assertThat(a.get("nachname")).isEqualTo("Max Mustermann");
                assertThat(a.get("aufgabe")).isEqualTo("Vertreter");
                assertThat(a.get("vorname")).isNull();
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM lieferant_ansprechpartner WHERE lieferant_id = ?",
                    Long.class, ohneVertreter)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'lieferanten' "
                    + "AND column_name = 'vertreter'", Long.class)).isZero();
        } finally {
            for (Long id : new Long[] { mitVertreter, ohneVertreter }) {
                if (id != null) {
                    jdbc.update("DELETE FROM lieferanten WHERE id = ?", id);
                }
            }
            jdbc.execute("ALTER TABLE lieferanten DROP COLUMN IF EXISTS vertreter");
            jdbc.execute("DROP TABLE IF EXISTS " + HISTORIE);
        }
    }

    /** Nur V450, mit eigener Historientabelle (jeder Aufruf führt sie erneut aus). */
    private void migriereV450() {
        new JdbcTemplate(dataSource).execute("DROP TABLE IF EXISTS " + HISTORIE);
        var ergebnis = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + ordner.toAbsolutePath())
                .table(HISTORIE)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
        assertThat(ergebnis.migrationsExecuted).isEqualTo(1);
    }
}
