package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Kunden-Installationen laufen auf PostgreSQL, der eigene Server auf MySQL.
 * Beide bekommen dieselben Nachtupdates - deshalb braucht jede neue
 * MySQL-Migration ein PostgreSQL-Gegenstueck mit derselben Nummer.
 */
class PostgresMigrationslinieTest {

    private static final Path MYSQL_MIGRATIONEN = Path.of("src", "main", "resources", "db", "migration");
    private static final Path PG_MIGRATIONEN = Path.of("src", "main", "resources", "db", "postgresql", "migration");
    private static final Path PG_BASIS = Path.of("src", "main", "resources", "db", "postgresql", "basis", "V1__basis_schema.sql");

    /**
     * Alles bis hierhin steckt schon in der PostgreSQL-Basis. Gleiche Zahl in
     * scripts/basis-schema/postgres_erzeugen.sh (Pruefung vor dem Erzeugen).
     */
    static final int LETZTE_MIGRATION_VOR_POSTGRES = 406;

    private static final Path MYSQL_BASIS = Path.of("src", "main", "resources", "db", "basis", "V1__basis_schema.sql");
    /** Sicherungstabellen alter Migrationen - nur in MySQL, von der App nicht genutzt. */
    private static final Set<String> NUR_MYSQL = Set.of("artikel_bereinigung_backup", "lieferanten_artikel_preise_korrektur_v360");
    private static final String REGEL = "(?: ON DELETE (CASCADE|SET NULL|RESTRICT|NO ACTION))?+";
    private static final Pattern MYSQL_TABELLE = Pattern.compile("^CREATE TABLE `([^`]++)`");
    private static final Pattern MYSQL_FK = Pattern.compile(
            "FOREIGN KEY \\(([^)]++)\\) REFERENCES `([^`]++)` \\(([^)]++)\\)" + REGEL);
    private static final Pattern PG_ALTER_TABLE = Pattern.compile("^ALTER TABLE ONLY public\\.(\\w++)$");
    private static final Pattern PG_FK = Pattern.compile(
            "FOREIGN KEY \\(([^)]++)\\) REFERENCES public\\.(\\w++)\\(([^)]++)\\)" + REGEL);

    private static final Pattern DATEINAME =Pattern.compile("V(\\d++)__[A-Za-z0-9_]++\\.sql");

    /** version, script, checksum aus den INSERTs von pg_dump --column-inserts */
    private static final Pattern PG_HISTORIE = Pattern.compile(
            "INSERT INTO public\\.flyway_schema_history \\([^)]*+\\) VALUES \\(\\d++, '(\\d++)', '[^']*+', 'SQL', '(V\\d++__[A-Za-z0-9_]++\\.sql)', (-?\\d++),");

    /** Versionsnummer -> Dateiname aller Migrationen eines Ordners. */
    private static Map<Integer, String> migrationen(Path ordner) throws IOException {
        Map<Integer, String> ergebnis = new TreeMap<>();
        if (!Files.isDirectory(ordner)) {
            return ergebnis;
        }
        try (Stream<Path> dateien = Files.list(ordner)) {
            for (Path datei : dateien.toList()) {
                Matcher treffer = DATEINAME.matcher(datei.getFileName().toString());
                if (treffer.matches()) {
                    String vorher = ergebnis.put(Integer.parseInt(treffer.group(1)), datei.getFileName().toString());
                    assertThat(vorher).as("Versionsnummer doppelt in %s: %s", ordner, datei.getFileName()).isNull();
                }
            }
        }
        return ergebnis;
    }

    @Nested
    @DisplayName("Zwillings-Migrationen")
    class Zwillinge {

        @Test
        @DisplayName("Jede neue MySQL-Migration hat ein PostgreSQL-Gegenstueck mit derselben Nummer")
        void jedeMysqlMigrationHatZwilling() throws IOException {
            Map<Integer, String> pg = migrationen(PG_MIGRATIONEN);
            List<String> ohneZwilling = new ArrayList<>();
            migrationen(MYSQL_MIGRATIONEN).forEach((version, datei) -> {
                if (version > LETZTE_MIGRATION_VOR_POSTGRES && !pg.containsKey(version)) {
                    ohneZwilling.add(datei);
                }
            });
            assertThat(ohneZwilling)
                    .as("Fehlt in db/postgresql/migration (Regel siehe README dort)")
                    .isEmpty();
        }

        @Test
        @DisplayName("Keine PostgreSQL-Migration ohne MySQL-Gegenstueck")
        void keinePostgresMigrationOhneMysql() throws IOException {
            Map<Integer, String> mysql = migrationen(MYSQL_MIGRATIONEN);
            assertThat(migrationen(PG_MIGRATIONEN).keySet())
                    .as("Versionen nur auf PostgreSQL - beide Linien muessen gleich nummeriert sein")
                    .allMatch(mysql::containsKey);
        }

        @Test
        @DisplayName("PostgreSQL-Migrationen beginnen erst nach der Basis")
        void keineAltenNummern() throws IOException {
            assertThat(migrationen(PG_MIGRATIONEN).keySet())
                    .allMatch(version -> version > LETZTE_MIGRATION_VOR_POSTGRES);
        }
    }

    @Nested
    @DisplayName("PostgreSQL-Basis-Datei")
    class Basis {

        @Test
        @DisplayName("Basis ist vorhanden und enthaelt das komplette Schema")
        void vollstaendig() throws IOException {
            String basis = Files.readString(PG_BASIS, StandardCharsets.UTF_8);
            assertThat(Pattern.compile("(?m)^CREATE TABLE ").matcher(basis).results().count())
                    .isGreaterThan(100);
            assertThat(basis).contains("PostgreSQL database dump complete");
        }

        @Test
        @DisplayName("Basis enthaelt keine E-Mail-Adressen (keine Personendaten)")
        void keinePersonendaten() throws IOException {
            String basis = Files.readString(PG_BASIS, StandardCharsets.UTF_8);
            assertThat(FlywayStartSetupConfigTest.BasisSchema.EMAIL.matcher(basis).find())
                    .as("E-Mail-Adresse im PostgreSQL-Basis-Schema").isFalse();
        }

        @Test
        @DisplayName("Basis loescht nichts und verstellt die Verbindung nicht")
        void keinDropKeinPsqlBefehl() throws IOException {
            String basis = Files.readString(PG_BASIS, StandardCharsets.UTF_8);
            assertThat(basis).doesNotContainPattern("(?i)\\bDROP\\s++(TABLE|DATABASE|SCHEMA)\\b");
            // psql-Befehle (\restrict ...) versteht Flyway nicht; ein geleerter
            // search_path wuerde die Verbindung fuer die App unbrauchbar machen
            assertThat(basis).doesNotContainPattern("(?m)^\\\\");
            assertThat(basis).doesNotContain("set_config('search_path'");
        }

        @Test
        @DisplayName("Jede Migration der Basis-Historie existiert unveraendert (Checksumme wie Flyway)")
        void historiePasstZuDenMigrationen() throws IOException {
            String basis = Files.readString(PG_BASIS, StandardCharsets.UTF_8);
            Matcher treffer = PG_HISTORIE.matcher(basis);
            while (treffer.find()) {
                Path datei = PG_MIGRATIONEN.resolve(treffer.group(2));
                assertThat(datei).as("Migration aus der Basis-Historie fehlt").exists();
                assertThat(FlywayStartSetupConfigTest.BasisSchema.flywayChecksumme(datei))
                        .as("%s wurde nach dem Erzeugen der Basis geaendert - bestehende Migrationen nie aendern", datei)
                        .isEqualTo(Integer.parseInt(treffer.group(3)));
            }
        }

        @Test
        @DisplayName("Fremdschluessel und Loeschregeln (CASCADE / SET NULL) wie in der MySQL-Basis")
        void fremdschluesselWieMysql() throws IOException {
            Set<String> mysql = mysqlFremdschluessel();
            Set<String> postgres = postgresFremdschluessel();
            assertThat(mysql).as("MySQL-Basis ohne Fremdschluessel - Test waere wertlos").hasSizeGreaterThan(100);
            // Gleiche Menge in beide Richtungen: Ein Loeschen, das auf dem eigenen
            // MySQL-Server klappt, muss bei Kunden genauso klappen (und umgekehrt)
            assertThat(postgres).as("PostgreSQL-Basis weicht ab - neu erzeugen (postgres_erzeugen.sh)")
                    .containsExactlyInAnyOrderElementsOf(mysql);
        }

        @Test
        @DisplayName("Keine Enum-CHECKs (Basis und Migrationen) - sie wuerden neue Enum-Werte nur bei Kunden blockieren")
        void keineEnumChecks() throws IOException {
            String basis = Files.readString(PG_BASIS, StandardCharsets.UTF_8);
            assertThat(basis).doesNotContain("= ANY ((ARRAY[");
            // MySQL hat fuer Java-Enums keine CHECKs (VARCHAR bzw. ENUM-Typ) - die
            // PostgreSQL-Zwillinge sollen auch keine anlegen
            for (String datei : migrationen(PG_MIGRATIONEN).values()) {
                assertThat(Files.readString(PG_MIGRATIONEN.resolve(datei), StandardCharsets.UTF_8))
                        .as("%s: CHECK mit Werteliste - Enum-Werte nicht in PostgreSQL festschreiben", datei)
                        .doesNotContainPattern("(?i)CHECK\\s*+\\([^)]*+\\bIN\\s*+\\(");
            }
        }

        /**
         * "tabelle(spalten)->ziel(spalten) REGEL" aus mysqldump (FKs stehen im CREATE TABLE).
         * Zwei Fremdschluessel auf dieselben Spalten: In MySQL blockiert dann der
         * ohne Loeschregel (ausprobiert) - so zaehlt es hier auch.
         */
        private Set<String> mysqlFremdschluessel() throws IOException {
            Map<String, String> regelJeVerbindung = new TreeMap<>();
            String tabelle = null;
            for (String zeile : Files.readAllLines(MYSQL_BASIS, StandardCharsets.UTF_8)) {
                Matcher neueTabelle = MYSQL_TABELLE.matcher(zeile);
                if (neueTabelle.find()) {
                    tabelle = neueTabelle.group(1);
                    continue;
                }
                Matcher fk = MYSQL_FK.matcher(zeile);
                if (fk.find() && !NUR_MYSQL.contains(tabelle)) {
                    String eintrag = fremdschluessel(tabelle, fk.group(1), fk.group(2), fk.group(3), fk.group(4));
                    String verbindung = eintrag.substring(0, eintrag.lastIndexOf(") ") + 1);
                    String regel = eintrag.substring(verbindung.length() + 1);
                    regelJeVerbindung.merge(verbindung, regel,
                            (alt, neu) -> "NO ACTION".equals(alt) || "NO ACTION".equals(neu) ? "NO ACTION" : alt);
                }
            }
            Set<String> ergebnis = new TreeSet<>();
            regelJeVerbindung.forEach((verbindung, regel) -> ergebnis.add(verbindung + " " + regel));
            return ergebnis;
        }

        /** Dasselbe aus pg_dump (FKs als ALTER TABLE ... ADD CONSTRAINT ueber zwei Zeilen). */
        private Set<String> postgresFremdschluessel() throws IOException {
            Set<String> ergebnis = new TreeSet<>();
            String tabelle = null;
            for (String zeile : Files.readAllLines(PG_BASIS, StandardCharsets.UTF_8)) {
                Matcher alter = PG_ALTER_TABLE.matcher(zeile);
                if (alter.find()) {
                    tabelle = alter.group(1);
                    continue;
                }
                Matcher fk = PG_FK.matcher(zeile);
                if (fk.find()) {
                    ergebnis.add(fremdschluessel(tabelle, fk.group(1), fk.group(2), fk.group(3), fk.group(4)));
                }
            }
            return ergebnis;
        }

        private static String fremdschluessel(String tabelle, String spalten, String ziel, String zielspalten, String regel) {
            // RESTRICT und NO ACTION verhalten sich (ohne verzoegerte Pruefung) gleich
            String loeschen = regel == null || "RESTRICT".equals(regel) ? "NO ACTION" : regel;
            return tabelle + "(" + spalten.replaceAll("[`\" ]", "") + ")->" + ziel + "("
                    + zielspalten.replaceAll("[`\" ]", "") + ") " + loeschen;
        }

        @Test
        @DisplayName("Das Historien-Muster passt zur Ausgabe von pg_dump (sonst waere der Test oben wertlos)")
        void historienMusterSchlaegtAn() {
            String zeile = "INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, "
                    + "checksum, installed_by, installed_on, execution_time, success) VALUES "
                    + "(1, '407', 'beispiel', 'SQL', 'V407__beispiel.sql', -12345, 'basis-schema', '2026-01-01 00:00:00', 0, true);";
            Matcher treffer = PG_HISTORIE.matcher(zeile);
            assertThat(treffer.find()).isTrue();
            assertThat(treffer.group(2)).isEqualTo("V407__beispiel.sql");
            assertThat(treffer.group(3)).isEqualTo("-12345");
        }
    }
}
