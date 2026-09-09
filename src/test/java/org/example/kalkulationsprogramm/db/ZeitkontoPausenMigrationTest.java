package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prüft V370 zusätzlich zum separaten MySQL-8-Lauf.
 * H2 kennt das MySQL-Schlüsselwort STORED nicht; nur dieses wird entfernt.
 */
class ZeitkontoPausenMigrationTest {
    @Test void migrationIstWiederholbarUndErzwingtGueltigeEindeutigePausen() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V370__zeitkonto_pausen.sql");
             var connection = DriverManager.getConnection("jdbc:h2:mem:zeitkontoPausenMigration;MODE=MySQL", "sa", "");
             var statement = connection.createStatement()) {
            assertNotNull(input);
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace(" STORED", "");
            statement.execute("CREATE TABLE mitarbeiter (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO mitarbeiter VALUES (1), (2)");
            statement.execute(migration); statement.execute(migration);
            statement.execute("INSERT INTO zeitkonto_pause(mitarbeiter_id, gueltig_von) VALUES(1, '2026-09-01')");
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO zeitkonto_pause(mitarbeiter_id, gueltig_von) VALUES(1, '2026-09-02')"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO zeitkonto_pause(mitarbeiter_id, gueltig_von, gueltig_bis) VALUES(2, '2026-09-02', '2026-09-01')"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO zeitkonto_pause(mitarbeiter_id, gueltig_von) VALUES(999, '2026-09-01')"));
            statement.execute("UPDATE zeitkonto_pause SET gueltig_bis = '2026-09-01' WHERE mitarbeiter_id = 1");
            statement.execute("INSERT INTO zeitkonto_pause(mitarbeiter_id, gueltig_von) VALUES(1, '2026-09-03')");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM zeitkonto_pause")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
            }
        }
    }
}
