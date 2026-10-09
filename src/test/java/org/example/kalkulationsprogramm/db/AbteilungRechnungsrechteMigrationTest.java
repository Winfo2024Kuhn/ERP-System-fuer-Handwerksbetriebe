package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuehrt die echte INSERT-Anweisung aus V406 auf isoliertem H2 (MySQL-Modus) aus.
 * Die MySQL-eigene {@code SET @var = IF(...); PREPARE ... FROM @s; EXECUTE ...}-Steuerung
 * (inkl. information_schema-Existenzpruefung) versteht H2 nicht -- deshalb wird die
 * eigentliche Anweisung per Regex aus ihrem String-Literal gezogen (Muster:
 * KasseBelegeMigrationTest). Geprueft werden die Daten-Semantik (welche Abteilungen,
 * welche Typen, welche Flags, nichts ueberschreiben, idempotent).
 */
class AbteilungRechnungsrechteMigrationTest {

    private static final String MIGRATION = "V406__abteilungen_rechnungsrechte_seed.sql";

    @Test
    void seedLegtNurFehlendeRechnungsrechteAnUndIstIdempotent() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:rechnungsrechteSeed;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            Statement s = c.createStatement();

            s.execute("CREATE TABLE abteilung (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, "
                    + "darf_rechnungen_sehen BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "darf_rechnungen_genehmigen BOOLEAN NOT NULL DEFAULT FALSE)");
            s.execute("CREATE TABLE abteilung_dokument_berechtigung (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "abteilung_id BIGINT NOT NULL, dokument_typ VARCHAR(50) NOT NULL, "
                    + "darf_sehen BOOLEAN NOT NULL DEFAULT FALSE, darf_scannen BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "CONSTRAINT uk_abt_dok UNIQUE (abteilung_id, dokument_typ))");

            // 1 = nur Sehen-Flag, 2 = nur Genehmigen-Flag, 3 = beide, 4 = kein Flag,
            // 5 = Sehen-Flag + bereits abweichende Zeile fuer RECHNUNG (darf nicht ueberschrieben werden)
            s.executeUpdate("INSERT INTO abteilung (id, name, darf_rechnungen_sehen, darf_rechnungen_genehmigen) VALUES "
                    + "(1, 'Buchhaltung Test', TRUE, FALSE), "
                    + "(2, 'Buero Test', FALSE, TRUE), "
                    + "(3, 'Geschaeftsfuehrung Test', TRUE, TRUE), "
                    + "(4, 'Werkstatt Test', FALSE, FALSE), "
                    + "(5, 'Einkauf Test', TRUE, FALSE)");
            s.executeUpdate("INSERT INTO abteilung_dokument_berechtigung (abteilung_id, dokument_typ, darf_sehen, darf_scannen) "
                    + "VALUES (5, 'RECHNUNG', FALSE, TRUE)");
            // Fremde Typen bleiben ebenfalls unberuehrt.
            s.executeUpdate("INSERT INTO abteilung_dokument_berechtigung (abteilung_id, dokument_typ, darf_sehen, darf_scannen) "
                    + "VALUES (1, 'BELEG', TRUE, TRUE)");

            String insert = seedInsert();
            for (int lauf = 0; lauf < 2; lauf++) {
                s.execute(insert); // zweiter Lauf: Idempotenz
            }

            for (long abteilungId : new long[] {1, 2, 3}) {
                assertZeile(s, abteilungId, "RECHNUNG", true, false);
                assertZeile(s, abteilungId, "GUTSCHRIFT", true, false);
            }
            assertThat(anzahl(s, "abteilung_id = 4")).as("Abteilung ohne Rechnungs-Flag bekommt nichts").isZero();

            // Abteilung 5: vorhandene RECHNUNG-Zeile unveraendert (darf_sehen=FALSE, darf_scannen=TRUE),
            // GUTSCHRIFT wird ergaenzt.
            assertZeile(s, 5, "RECHNUNG", false, true);
            assertZeile(s, 5, "GUTSCHRIFT", true, false);

            // Fremder Eintrag unveraendert
            assertZeile(s, 1, "BELEG", true, true);

            // Genau eine Zeile je (Abteilung, Typ) trotz zwei Laeufen:
            // 1: RECHNUNG+GUTSCHRIFT+BELEG, 2: 2, 3: 2, 5: 2  -> 9
            assertThat(anzahl(s, "1 = 1")).isEqualTo(9);
        }
    }

    private void assertZeile(Statement s, long abteilungId, String typ, boolean sehen, boolean scannen) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT darf_sehen, darf_scannen FROM abteilung_dokument_berechtigung "
                + "WHERE abteilung_id = " + abteilungId + " AND dokument_typ = '" + typ + "'")) {
            assertThat(rs.next()).as("Zeile (%d, %s) existiert", abteilungId, typ).isTrue();
            assertThat(rs.getBoolean(1)).as("darf_sehen (%d, %s)", abteilungId, typ).isEqualTo(sehen);
            assertThat(rs.getBoolean(2)).as("darf_scannen (%d, %s)", abteilungId, typ).isEqualTo(scannen);
            assertThat(rs.next()).as("genau eine Zeile (%d, %s)", abteilungId, typ).isFalse();
        }
    }

    private int anzahl(Statement s, String where) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM abteilung_dokument_berechtigung WHERE " + where)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Zieht die INSERT-Anweisung aus dem String-Literal des PREPARE-Musters. */
    private String seedInsert() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertThat(stream).as("Migration %s existiert", MIGRATION).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        }
        var matcher = Pattern.compile("'(INSERT INTO abteilung_dokument_berechtigung(?:[^']|'')*)'", Pattern.DOTALL)
                .matcher(sql);
        List<String> statements = new ArrayList<>();
        while (matcher.find()) {
            statements.add(matcher.group(1).replace("''", "'"));
        }
        assertThat(statements).as("genau eine INSERT-Anweisung in V406").hasSize(1);
        return statements.get(0);
    }
}
