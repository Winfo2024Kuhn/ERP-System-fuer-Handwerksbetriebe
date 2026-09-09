package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuehrt die echten ALTER-/INSERT-/UPDATE-Anweisungen aus V372 auf isoliertem
 * H2 (MySQL-Kompatibilitaet) aus. Die MySQL-eigene
 * {@code SET @var = IF(...); PREPARE ... FROM @s; EXECUTE ...}-Steuerung
 * versteht H2 nicht -- deshalb werden die eigentlichen SQL-Anweisungen per
 * Regex aus ihren String-Literalen gezogen (Muster: ZeitkontoMigrationTest).
 *
 * Bewusst KEIN {@code sql.contains("spaltenname")}: der Spaltenname taucht
 * legitim auch im Kopfkommentar auf. Stattdessen werden die Anweisungen
 * wirklich ausgefuehrt und das entstandene Schema/die entstandenen Daten
 * geprueft.
 */
public class KasseBelegeMigrationTest {

    @Test
    void alterSpaltenBackfillUndSeedLaufenIdempotentUndErwartungsgemaess() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:kasseBelege;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            Statement s = c.createStatement();

            // Minimal-Tabellen, wie sie vor V372 im Schema stehen.
            s.execute("CREATE TABLE beleg (id BIGINT PRIMARY KEY, zahlungsart VARCHAR(40), " +
                    "ist_umbuchung BOOLEAN NOT NULL DEFAULT FALSE)");
            s.execute("CREATE TABLE kasse_einstellung (id BIGINT PRIMARY KEY)");
            s.execute("CREATE TABLE projekt_geschaeftsdokument (id BIGINT PRIMARY KEY)");
            s.execute("CREATE TABLE zahlungsart (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "bezeichnung VARCHAR(60) NOT NULL, aktiv BOOLEAN NOT NULL DEFAULT TRUE, " +
                    "sortierung INT NOT NULL DEFAULT 0, CONSTRAINT uk_zahlungsart_bezeichnung UNIQUE (bezeichnung))");
            s.executeUpdate("INSERT INTO zahlungsart (bezeichnung, aktiv, sortierung) VALUES " +
                    "('Bar', TRUE, 10), ('EC-Karte', TRUE, 20), ('Überweisung', TRUE, 30)");
            s.executeUpdate("INSERT INTO kasse_einstellung (id) VALUES (1)");

            String schema = migration("V372__kasse_buchungen_und_export.sql");

            // 1) ALTER-/FK-Anweisungen aus ihren String-Literalen ziehen und einmalig ausfuehren
            //    (die MySQL-Idempotenz-Pruefung selbst ist auf H2 nicht ausfuehrbar -- die
            //    Absicherung dafuer ist das Muster aus V351/V305, hier nicht erneut geprueft).
            var alters = Pattern.compile("'(ALTER TABLE (?:[^']|'')*)'", Pattern.DOTALL).matcher(schema);
            int anzahlAlters = 0;
            while (alters.find()) {
                s.execute(alters.group(1).replace("''", "'"));
                anzahlAlters++;
            }
            assertThat(anzahlAlters).as("Anzahl extrahierter ALTER-TABLE-Anweisungen "
                            + "(4 beleg-Basisspalten + FK-Constraint + 4 ki_*-Spalten + 5 kasse_einstellung-Spalten)")
                    .isEqualTo(13);

            // Testdaten fuer den Backfill -- nach den ALTERs, weil die neue Spalte "quelle" erst
            // jetzt existiert.
            s.executeUpdate("INSERT INTO beleg (id, zahlungsart, ist_umbuchung) VALUES " +
                    "(1, 'BAR', FALSE), " +
                    "(2, 'UEBERWEISUNG', FALSE), " +
                    "(3, 'SEPA_LASTSCHRIFT', FALSE), " +
                    "(4, 'AMAZON_PAY', FALSE), " +
                    "(5, 'VORAUSKASSE', FALSE), " +
                    "(6, 'SONSTIGE', FALSE), " +
                    "(7, 'Bar', FALSE), " +          // bereits Klartext -- muss unveraendert bleiben
                    "(8, NULL, TRUE), " +             // Umbuchung ohne Zahlungsart -> quelle wird TRANSFER
                    "(9, 'BAR', FALSE)");             // normaler Beleg -> quelle bleibt SCAN

            // 2) INSERT IGNORE-/UPDATE-Backfill zweimal ausfuehren -- genau das prueft die im
            //    Plan geforderte Idempotenz (ELSE-Zweig bzw. UNIQUE(bezeichnung)).
            var dml = Pattern.compile("(INSERT IGNORE[^;]*;|UPDATE beleg[^;]*;)", Pattern.DOTALL).matcher(schema);
            java.util.List<String> dmlStatements = new java.util.ArrayList<>();
            while (dml.find()) {
                dmlStatements.add(dml.group(1));
            }
            assertThat(dmlStatements).as("INSERT IGNORE (Stammdaten-Seed) + 2 UPDATE-Backfills").hasSize(3);
            for (int lauf = 0; lauf < 2; lauf++) {
                for (String statement : dmlStatements) {
                    s.execute(statement);
                }
            }

            // ---------- Assertions: Spalten existieren mit erwartetem Typ ----------
            assertSpalte(c, "beleg", "quelle", "ENUM");
            assertSpalte(c, "beleg", "gegenpartei", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "beleg", "ausgangsrechnung_id", "BIGINT");
            assertSpalte(c, "beleg", "ki_zahlungsart", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "beleg", "ki_belegdatum", "DATE");
            assertSpalte(c, "beleg", "ki_betrag_brutto", "DECIMAL", "NUMERIC");
            assertSpalte(c, "beleg", "ki_kostenkonto_hinweis", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "kasse_einstellung", "datev_beraternummer", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "kasse_einstellung", "datev_mandantennummer", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "kasse_einstellung", "wirtschaftsjahr_beginn_monat", "INT", "INTEGER");
            assertSpalte(c, "kasse_einstellung", "kassenkonto_nummer", "VARCHAR", "CHARACTER VARYING");
            assertSpalte(c, "kasse_einstellung", "bankkonto_nummer", "VARCHAR", "CHARACTER VARYING");

            // ---------- Assertions: FK auf projekt_geschaeftsdokument existiert ----------
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                    "WHERE UPPER(TABLE_NAME) = 'BELEG' AND UPPER(CONSTRAINT_NAME) = 'FK_BELEG_AUSGANGSRECHNUNG'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("FK fk_beleg_ausgangsrechnung existiert").isEqualTo(1);
            }

            // ---------- Assertions: Zahlungsart-Backfill je Zeile ----------
            assertZahlungsart(s, 1, "Bar");
            assertZahlungsart(s, 2, "Überweisung");
            assertZahlungsart(s, 3, "Lastschrift");
            assertZahlungsart(s, 4, "Online-Zahlung");
            assertZahlungsart(s, 5, "Überweisung");
            assertZahlungsart(s, 6, null);       // SONSTIGE -> NULL, Nutzer muss beim Pruefen waehlen
            assertZahlungsart(s, 7, "Bar");       // bereits Klartext, unveraendert (ELSE-Zweig)

            // ---------- Assertions: quelle-Backfill (ist_umbuchung -> TRANSFER) ----------
            try (ResultSet rs = s.executeQuery("SELECT quelle FROM beleg WHERE id = 8")) {
                rs.next();
                assertThat(rs.getString(1)).as("Umbuchung wird zu TRANSFER").isEqualTo("TRANSFER");
            }
            try (ResultSet rs = s.executeQuery("SELECT quelle FROM beleg WHERE id = 9")) {
                rs.next();
                assertThat(rs.getString(1)).as("normaler Beleg bleibt SCAN").isEqualTo("SCAN");
            }

            // ---------- Assertions: Online-Zahlung genau einmal in zahlungsart, trotz 2 Laeufen ----------
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM zahlungsart WHERE bezeichnung = 'Online-Zahlung'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("Online-Zahlung genau einmal angelegt, trotz zwei Laeufen").isEqualTo(1);
            }
        }
    }

    private void assertZahlungsart(Statement s, long belegId, String erwartet) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT zahlungsart FROM beleg WHERE id = " + belegId)) {
            rs.next();
            assertThat(rs.getString(1)).as("beleg.id=%d", belegId).isEqualTo(erwartet);
        }
    }

    private void assertSpalte(Connection c, String tabelle, String spalte, String... erwarteteTypen) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE UPPER(TABLE_NAME) = '"
                             + tabelle.toUpperCase(java.util.Locale.ROOT) + "' AND UPPER(COLUMN_NAME) = '"
                             + spalte.toUpperCase(java.util.Locale.ROOT) + "'")) {
            assertThat(rs.next()).as("Spalte %s.%s existiert", tabelle, spalte).isTrue();
            String tatsaechlich = rs.getString(1).toUpperCase(java.util.Locale.ROOT);
            boolean treffer = false;
            for (String erwartet : erwarteteTypen) {
                if (tatsaechlich.contains(erwartet)) {
                    treffer = true;
                    break;
                }
            }
            assertThat(treffer)
                    .as("Spalte %s.%s hat Typ %s, erwartet einer von %s", tabelle, spalte, tatsaechlich, java.util.Arrays.toString(erwarteteTypen))
                    .isTrue();
        }
    }

    private String migration(String file) throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + file)) {
            assertThat(stream).as("Migration %s existiert", file).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        }
    }
}
