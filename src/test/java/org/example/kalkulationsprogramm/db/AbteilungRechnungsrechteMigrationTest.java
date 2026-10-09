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
 * Fuehrt die echte INSERT-Anweisung aus V406 auf isoliertem H2 (MySQL-Modus) aus.
 * Die MySQL-eigene {@code SET @var = IF(...); PREPARE ... FROM @s; EXECUTE ...}-Steuerung
 * (inkl. information_schema-Existenzpruefung) versteht H2 nicht -- deshalb wird die
 * eigentliche Anweisung per Regex aus ihrem String-Literal gezogen (Muster:
 * KasseBelegeMigrationTest). Geprueft werden die Daten-Semantik (welche Abteilungen,
 * welche Typen, welche Flags, nichts ueberschreiben, Alt-Name EINGANGSRECHNUNG, idempotent).
 * Der information_schema-Guard (@spalten_ok/@typ_ok) ist reines MySQL und hier nicht abgedeckt.
 */
class AbteilungRechnungsrechteMigrationTest {

    private static final String MIGRATION = "V406__abteilungen_rechnungsrechte_seed.sql";

    @Test
    void seedLegtNurFehlendeRechnungsrechteAnUndIstIdempotent() throws Exception {
        try (Connection c = neueDatenbank()) {
            // 1 = nur Sehen-Flag, 2 = nur Genehmigen-Flag, 3 = beide, 4 = kein Flag,
            // 5 = Sehen-Flag + bereits abweichende Zeile fuer RECHNUNG (darf nicht ueberschrieben werden)
            abteilung(c, 1, "Buchhaltung Test", true, false);
            abteilung(c, 2, "Buero Test", false, true);
            abteilung(c, 3, "Geschaeftsfuehrung Test", true, true);
            abteilung(c, 4, "Werkstatt Test", false, false);
            abteilung(c, 5, "Einkauf Test", true, false);
            berechtigung(c, 5, "RECHNUNG", false, true);
            // Fremde Typen bleiben ebenfalls unberuehrt.
            berechtigung(c, 1, "BELEG", true, true);

            seedAusfuehren(c, 2); // zweiter Lauf: Idempotenz

            for (long abteilungId : new long[] {1, 2, 3}) {
                assertZeile(c, abteilungId, "RECHNUNG", true, false);
                assertZeile(c, abteilungId, "GUTSCHRIFT", true, false);
            }
            assertThat(anzahlJeAbteilung(c, 4)).as("Abteilung ohne Rechnungs-Flag bekommt nichts").isZero();

            // Abteilung 5: vorhandene RECHNUNG-Zeile unveraendert (darf_sehen=FALSE, darf_scannen=TRUE),
            // GUTSCHRIFT wird ergaenzt.
            assertZeile(c, 5, "RECHNUNG", false, true);
            assertZeile(c, 5, "GUTSCHRIFT", true, false);

            // Fremder Eintrag unveraendert
            assertZeile(c, 1, "BELEG", true, true);

            // Genau eine Zeile je (Abteilung, Typ) trotz zwei Laeufen:
            // 1: RECHNUNG+GUTSCHRIFT+BELEG, 2: 2, 3: 2, 5: 2  -> 9
            assertThat(gesamtAnzahl(c)).isEqualTo(9);
        }
    }

    /**
     * Dokumentiert das beschriebene Verhalten: Hat die Admin-Oberflaeche beim Speichern fuer eine
     * Abteilung bereits RECHNUNG-/GUTSCHRIFT-Zeilen mit darf_sehen=FALSE angelegt, bleiben diese
     * unveraendert -- die Abteilung sieht Offene Posten & Co. weiter nicht, bis ein Admin den Haken setzt.
     */
    @Test
    void vorhandeneZeilenMitDarfSehenFalseAusOberflaechenSpeichernBleibenFalse() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Buchhaltung Test", true, true);
            berechtigung(c, 1, "RECHNUNG", false, false);
            berechtigung(c, 1, "GUTSCHRIFT", false, false);
            berechtigung(c, 1, "BELEG", false, false);

            seedAusfuehren(c, 2);

            assertZeile(c, 1, "RECHNUNG", false, false);
            assertZeile(c, 1, "GUTSCHRIFT", false, false);
            assertZeile(c, 1, "BELEG", false, false);
            assertThat(gesamtAnzahl(c)).isEqualTo(3);
        }
    }

    /**
     * Alt-Name EINGANGSRECHNUNG wird vom Code als RECHNUNG gelesen. Existiert er, darf keine zweite
     * RECHNUNG-Zeile entstehen -- GUTSCHRIFT wird dagegen normal ergaenzt.
     */
    @Test
    void altNameEingangsrechnungZaehltAlsVorhandeneRechnungszeile() throws Exception {
        try (Connection c = neueDatenbank()) {
            abteilung(c, 1, "Buchhaltung Test", true, false);
            berechtigung(c, 1, "EINGANGSRECHNUNG", false, false);

            seedAusfuehren(c, 2);

            assertZeile(c, 1, "EINGANGSRECHNUNG", false, false);
            assertThat(zeilen(c, 1, "RECHNUNG")).as("keine zweite RECHNUNG-Zeile").isZero();
            assertZeile(c, 1, "GUTSCHRIFT", true, false);
            assertThat(gesamtAnzahl(c)).isEqualTo(2);
        }
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
        String insert = seedInsert();
        try (Statement s = c.createStatement()) {
            for (int lauf = 0; lauf < laeufe; lauf++) {
                s.execute(insert);
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
