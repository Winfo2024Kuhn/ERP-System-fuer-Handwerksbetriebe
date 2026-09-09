package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Führt die echten CREATE/ALTER-Statements und die Bestands-DML auf isoliertem H2 aus.
 * MySQLs information_schema/PREPARE-Steuerung wird hier nicht ausgeführt;
 * STR_TO_DATE erhält einen Testadapter für das einzige verwendete Datumsformat.
 */
public class ZeitkontoMigrationTest {
    public static Date strToDate(String value, String format) {
        return Date.valueOf(LocalDate.parse(value));
    }

    @Test
    void bestandWirdEinmalMitAllenFallbacksUebernommenOhneSaldoAenderung() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:zeitkontoMigration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            Statement s = c.createStatement();
            s.execute("CREATE ALIAS STR_TO_DATE FOR 'org.example.kalkulationsprogramm.db.ZeitkontoMigrationTest.strToDate'");
            s.execute("CREATE TABLE mitarbeiter (id BIGINT PRIMARY KEY, eintrittsdatum DATE, login_token VARCHAR(255), aktiv BOOLEAN)");
            s.execute("CREATE TABLE abteilung (id BIGINT PRIMARY KEY)");
            s.execute("CREATE TABLE monats_saldo (id BIGINT PRIMARY KEY, mitarbeiter_id BIGINT, jahr INT, monat INT, ist_stunden DECIMAL(10,2), soll_stunden DECIMAL(10,2), gueltig BOOLEAN)");
            s.execute("CREATE TABLE abwesenheit (mitarbeiter_id BIGINT, datum DATE)");
            s.execute("CREATE TABLE zeitkonto_korrektur (mitarbeiter_id BIGINT, datum DATE)");
            s.execute("CREATE TABLE zeitbuchung (mitarbeiter_id BIGINT, start_zeit TIMESTAMP)");
            s.execute("CREATE TABLE zeitkonto (mitarbeiter_id BIGINT PRIMARY KEY, montag_stunden DECIMAL(4,2), dienstag_stunden DECIMAL(4,2), mittwoch_stunden DECIMAL(4,2), donnerstag_stunden DECIMAL(4,2), freitag_stunden DECIMAL(4,2), samstag_stunden DECIMAL(4,2), sonntag_stunden DECIMAL(4,2), buchung_start_zeit TIME, buchung_ende_zeit TIME)");
            try (PreparedStatement mitarbeiter = c.prepareStatement(
                    "INSERT INTO mitarbeiter VALUES (?, NULL, NULL, TRUE)");
                 PreparedStatement zeitkonto = c.prepareStatement(
                    "INSERT INTO zeitkonto VALUES (?, 7.25, 6.50, 5.75, 4.00, 3.50, NULL, 0.00, '06:15:00', NULL)")) {
                for (int id = 1; id <= 8; id++) {
                    mitarbeiter.setInt(1, id);
                    mitarbeiter.executeUpdate();
                    zeitkonto.setInt(1, id);
                    zeitkonto.executeUpdate();
                }
            }
            s.executeUpdate("UPDATE mitarbeiter SET eintrittsdatum = '2020-02-01' WHERE id = 1");
            s.executeUpdate("UPDATE mitarbeiter SET login_token = '__SYSTEM_FUNNEL__', aktiv = FALSE WHERE id = 7");
            s.executeUpdate("INSERT INTO monats_saldo VALUES (1, 1, 2019, 1, 123.45, 150.50, FALSE), (2, 2, 2018, 3, 42.25, 50.00, TRUE)");
            s.executeUpdate("INSERT INTO abwesenheit VALUES (3, '2017-04-02'), (2, '2020-01-01')");
            s.executeUpdate("INSERT INTO zeitkonto_korrektur VALUES (4, '2016-05-03'), (3, '2019-01-01')");
            s.executeUpdate("INSERT INTO zeitbuchung VALUES (5, '2015-06-04 09:30:00'), (4, '2021-01-01 10:00:00')");
            String schema = migration("V368__zeitkonto_datenfundament.sql");
            // Decode the static ALTER statements from their MySQL prepared string literals.
            var alters = Pattern.compile("'(ALTER TABLE (?:[^']|'')*)'", Pattern.DOTALL).matcher(schema);
            while (alters.find()) s.execute(alters.group(1).replace("''", "'"));
            var creates = Pattern.compile("CREATE TABLE IF NOT EXISTS .*?;", Pattern.DOTALL).matcher(schema);
            while (creates.find()) {
                s.execute(creates.group());
                s.execute(creates.group()); // CREATE TABLE part is idempotent.
            }
            s.executeUpdate("UPDATE mitarbeiter SET art = 'SYSTEM', fuehrt_zeitkonto = FALSE WHERE id = 8");
            String copy = migration("V369__zeitkonto_bestand_versionieren.sql");
            for (int run = 0; run < 2; run++) {
                for (String command : copy.split(";")) if (!command.isBlank()) s.execute(command);
            }
            try (ResultSet rs = s.executeQuery("SELECT mitarbeiter_id, gueltig_von, montag_stunden, samstag_stunden, buchung_start_zeit, buchung_ende_zeit, vorlage_id, gueltig_bis FROM zeitkonto_version ORDER BY mitarbeiter_id")) {
                String[] erwartet = {"2020-02-01", "2018-03-01", "2017-04-02", "2016-05-03", "2015-06-04", "1000-01-01"};
                for (int i = 0; i < erwartet.length; i++) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong(1)).isEqualTo(i + 1);
                    assertThat(rs.getObject(2, LocalDate.class)).isEqualTo(LocalDate.parse(erwartet[i]));
                    assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("7.25");
                    assertThat(rs.getObject(4)).isNull();
                    assertThat(rs.getTime(5).toString()).isEqualTo("06:15:00");
                    assertThat(rs.getObject(6)).isNull();
                    assertThat(rs.getObject(7)).isNull();
                    assertThat(rs.getObject(8)).isNull();
                }
                assertThat(rs.next()).isFalse();
            }
            try (ResultSet rs = s.executeQuery("SELECT art, aktiv, fuehrt_zeitkonto FROM mitarbeiter WHERE id = 7")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("SYSTEM");
                assertThat(rs.getBoolean(2)).isTrue(); assertThat(rs.getBoolean(3)).isFalse();
            }
            try (ResultSet rs = s.executeQuery("SELECT ist_stunden, soll_stunden, gueltig, festgeschrieben, festgeschrieben_am, festgeschrieben_von_mitarbeiter_id FROM monats_saldo ORDER BY id")) {
                for (int i = 0; i < 2; i++) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(i == 0 ? "123.45" : "42.25");
                    assertThat(rs.getBigDecimal(2)).isEqualByComparingTo(i == 0 ? "150.50" : "50.00");
                    assertThat(rs.getBoolean(3)).isEqualTo(i == 1);
                    assertThat(rs.getBoolean(4)).isFalse();
                    assertThat(rs.getObject(5)).isNull(); assertThat(rs.getObject(6)).isNull();
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM zeitkonto")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(8);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM zeitkontenmodell")) {
                rs.next(); assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private String migration(String file) throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + file)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        }
    }
}
