package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuehrt die echten UPDATE- und INSERT-Anweisungen aus V406 auf isoliertem H2 (MySQL-Modus) aus.
 * Die MySQL-eigene {@code SET @var = IF(...); PREPARE ... FROM @s; EXECUTE ...}-Steuerung
 * (inkl. information_schema-Existenzpruefung) versteht H2 nicht -- deshalb wird die
 * eigentliche Anweisung per Regex aus ihrem String-Literal gezogen (Muster:
 * KasseBelegeMigrationTest). Geprueft werden die Daten-Semantik (welche Abteilungen,
 * welche Typen, welche Flags, Freischalten vorhandener Zeilen, Alt-Name EINGANGSRECHNUNG, idempotent).
 * Der information_schema-Guard (@spalten_ok/@typ_ok) ist reines MySQL und hier nicht abgedeckt.
 */
class AbteilungRechnungsrechteMigrationTest {

    private static final String MIGRATION = "V406__abteilungen_rechnungsrechte_seed.sql";

    @Test
    void seedLegtFehlendeRechnungsrechteAnUndIstIdempotent() throws Exception {
        try (Connection c = neueDatenbank()) {
            // 1 = nur Sehen-Flag, 2 = nur Genehmigen-Flag, 3 = beide, 4 = kein Flag
            abteilung(c, 1, "Buchhaltung Test", true, false);
            abteilung(c, 2, "Buero Test", false, true);
            abteilung(c, 3, "Geschaeftsfuehrung Test", true, true);
            abteilung(c, 4, "Werkstatt Test", false, false);
            // Fremder Typ bleibt unberuehrt.
            berechtigung(c, 1, "BELEG", false, true);

            seedAusfuehren(c, 2); // zweiter Lauf: Idempotenz

            for (long abteilungId : new long[] {1, 2, 3}) {
                assertZeile(c, abteilungId, "RECHNUNG", true, false);
                assertZeile(c, abteilungId, "GUTSCHRIFT", true, false);
            }
            assertThat(anzahlJeAbteilung(c, 4)).as("Abteilung ohne Rechnungs-Flag bekommt nichts").isZero();
            assertZeile(c, 1, "BELEG", false, true);

            // 1: RECHNUNG+GUTSCHRIFT+BELEG, 2: 2, 3: 2 -> 7
            assertThat(gesamtAnzahl(c)).isEqualTo(7);
        }
    }

    /**
     * Vorhandene Zeilen mit darf_sehen=FALSE (z. B. aus dem Speichern der Berechtigungen-Seite) werden bei
     * Abteilungen mit Rechnungs-Flag auf TRUE gesetzt; darf_scannen bleibt, fehlende Typen werden ergaenzt.
     */
    @Test
    void vorhandeneZeilenMitDarfSehenFalseWerdenFreigeschaltet() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Einkauf Test", true, false);
            berechtigung(c, 1, "RECHNUNG", false, true);

            abteilung(c, 2, "Buchhaltung Test", false, true);
            berechtigung(c, 2, "RECHNUNG", false, false);
            berechtigung(c, 2, "GUTSCHRIFT", false, true);
            berechtigung(c, 2, "BELEG", false, false);

            seedAusfuehren(c, 2);

            assertZeile(c, 1, "RECHNUNG", true, true);   // scannen bleibt TRUE
            assertZeile(c, 1, "GUTSCHRIFT", true, false); // ergaenzt
            assertZeile(c, 2, "RECHNUNG", true, false);
            assertZeile(c, 2, "GUTSCHRIFT", true, true);
            assertZeile(c, 2, "BELEG", false, false);     // fremder Typ unberuehrt
            assertThat(gesamtAnzahl(c)).isEqualTo(5);
        }
    }

    /**
     * Alt-Name EINGANGSRECHNUNG wird vom Code als RECHNUNG gelesen: Die Zeile wird freigeschaltet, es entsteht
     * keine zweite RECHNUNG-Zeile; GUTSCHRIFT wird ergaenzt.
     */
    @Test
    void altNameEingangsrechnungWirdFreigeschaltetOhneZweiteRechnungszeile() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Buchhaltung Test", true, false);
            berechtigung(c, 1, "EINGANGSRECHNUNG", false, true);

            seedAusfuehren(c, 2);

            assertZeile(c, 1, "EINGANGSRECHNUNG", true, true);
            assertThat(zeilen(c, 1, "RECHNUNG")).as("keine zweite RECHNUNG-Zeile").isZero();
            assertZeile(c, 1, "GUTSCHRIFT", true, false);
            assertThat(gesamtAnzahl(c)).isEqualTo(2);
        }
    }

    /** Abteilungen ohne Rechnungs-Flag und fremde Dokumenttypen bleiben unveraendert (auch bei darf_sehen=FALSE). */
    @Test
    void abteilungOhneFlagUndFremdeTypenBleibenUnveraendert() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Werkstatt Test", false, false);
            berechtigung(c, 1, "RECHNUNG", false, false);
            berechtigung(c, 1, "GUTSCHRIFT", false, true);
            berechtigung(c, 1, "EINGANGSRECHNUNG", false, false);

            abteilung(c, 2, "Buchhaltung Test", true, true);
            berechtigung(c, 2, "BELEG", false, false);
            berechtigung(c, 2, "LIEFERSCHEIN", false, true);

            seedAusfuehren(c, 2);

            assertZeile(c, 1, "RECHNUNG", false, false);
            assertZeile(c, 1, "GUTSCHRIFT", false, true);
            assertZeile(c, 1, "EINGANGSRECHNUNG", false, false);
            assertZeile(c, 2, "BELEG", false, false);
            assertZeile(c, 2, "LIEFERSCHEIN", false, true);
            assertZeile(c, 2, "RECHNUNG", true, false);
            assertZeile(c, 2, "GUTSCHRIFT", true, false);
            assertThat(gesamtAnzahl(c)).isEqualTo(7);
        }
    }

    /** Der zweite Lauf aendert nichts mehr (nach dem ersten Lauf identischer Datenbestand). */
    @Test
    void zweiterLaufAendertNichts() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Buchhaltung Test", true, false);
            berechtigung(c, 1, "RECHNUNG", false, true);
            berechtigung(c, 1, "BELEG", false, false);

            seedAusfuehren(c, 1);
            String nachErstemLauf = snapshot(c);
            seedAusfuehren(c, 1);

            assertThat(snapshot(c)).isEqualTo(nachErstemLauf);
        }
    }

    private String snapshot(Connection c) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement("SELECT abteilung_id, dokument_typ, darf_sehen, darf_scannen "
                + "FROM abteilung_dokument_berechtigung ORDER BY abteilung_id, dokument_typ");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sb.append(rs.getLong(1)).append('|').append(rs.getString(2)).append('|')
                        .append(rs.getBoolean(3)).append('|').append(rs.getBoolean(4)).append('\n');
            }
        }
        return sb.toString();
    }

    /** Eindeutig benannte In-Memory-DB (ohne DB_CLOSE_DELAY): verschwindet mit dem Schliessen der Verbindung. */
    private Connection neueDatenbank() throws Exception {
        String name = "rechnungsrechteSeed_" + UUID.randomUUID().toString().replace("-", "");
        Connection c = DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL", "sa", "");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE abteilung (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, "
                    + "darf_rechnungen_sehen BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "darf_rechnungen_genehmigen BOOLEAN NOT NULL DEFAULT FALSE)");
            s.execute("CREATE TABLE abteilung_dokument_berechtigung (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "abteilung_id BIGINT NOT NULL, dokument_typ VARCHAR(50) NOT NULL, "
                    + "darf_sehen BOOLEAN NOT NULL DEFAULT FALSE, darf_scannen BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "CONSTRAINT uk_abt_dok UNIQUE (abteilung_id, dokument_typ))");
        }
        return c;
    }

    private void abteilung(Connection c, long id, String name, boolean sehen, boolean genehmigen) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO abteilung "
                + "(id, name, darf_rechnungen_sehen, darf_rechnungen_genehmigen) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setBoolean(3, sehen);
            ps.setBoolean(4, genehmigen);
            ps.executeUpdate();
        }
    }

    private void berechtigung(Connection c, long abteilungId, String typ, boolean sehen, boolean scannen)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO abteilung_dokument_berechtigung "
                + "(abteilung_id, dokument_typ, darf_sehen, darf_scannen) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, abteilungId);
            ps.setString(2, typ);
            ps.setBoolean(3, sehen);
            ps.setBoolean(4, scannen);
            ps.executeUpdate();
        }
    }

    private void seedAusfuehren(Connection c, int laeufe) throws Exception {
        List<String> statements = seedStatements(); // Reihenfolge wie in der Migration: UPDATE, dann INSERT
        try (Statement s = c.createStatement()) {
            for (int lauf = 0; lauf < laeufe; lauf++) {
                for (String statement : statements) {
                    s.execute(statement);
                }
            }
        }
    }

    private void assertZeile(Connection c, long abteilungId, String typ, boolean sehen, boolean scannen)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT darf_sehen, darf_scannen "
                + "FROM abteilung_dokument_berechtigung WHERE abteilung_id = ? AND dokument_typ = ?")) {
            ps.setLong(1, abteilungId);
            ps.setString(2, typ);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("Zeile (%d, %s) existiert", abteilungId, typ).isTrue();
                assertThat(rs.getBoolean(1)).as("darf_sehen (%d, %s)", abteilungId, typ).isEqualTo(sehen);
                assertThat(rs.getBoolean(2)).as("darf_scannen (%d, %s)", abteilungId, typ).isEqualTo(scannen);
                assertThat(rs.next()).as("genau eine Zeile (%d, %s)", abteilungId, typ).isFalse();
            }
        }
    }

    private int zeilen(Connection c, long abteilungId, String typ) throws Exception {
        return zaehle(c, "SELECT COUNT(*) FROM abteilung_dokument_berechtigung "
                + "WHERE abteilung_id = ? AND dokument_typ = ?", abteilungId, typ);
    }

    private int anzahlJeAbteilung(Connection c, long abteilungId) throws Exception {
        return zaehle(c, "SELECT COUNT(*) FROM abteilung_dokument_berechtigung WHERE abteilung_id = ?", abteilungId);
    }

    private int gesamtAnzahl(Connection c) throws Exception {
        return zaehle(c, "SELECT COUNT(*) FROM abteilung_dokument_berechtigung");
    }

    private int zaehle(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Zieht UPDATE und INSERT (in Dateireihenfolge) aus den String-Literalen der PREPARE-Muster. */
    private List<String> seedStatements() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertThat(stream).as("Migration %s existiert", MIGRATION).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        }
        var matcher = Pattern.compile(
                "'((?:UPDATE|INSERT INTO) abteilung_dokument_berechtigung(?:[^']|'')*)'", Pattern.DOTALL)
                .matcher(sql);
        List<String> statements = new ArrayList<>();
        while (matcher.find()) {
            statements.add(matcher.group(1).replace("''", "'"));
        }
        assertThat(statements).as("genau ein UPDATE und ein INSERT in V406").hasSize(2);
        assertThat(statements.get(0)).startsWith("UPDATE");
        assertThat(statements.get(1)).startsWith("INSERT");
        return statements;
    }
}
