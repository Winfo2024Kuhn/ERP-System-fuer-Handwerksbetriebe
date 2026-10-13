package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
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
import java.util.zip.CRC32;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL ist die einzige Datenbank. Migrationen liegen in
 * {@code db/postgresql/migration}, Neuinstallationen bekommen vorher die Basis
 * {@code db/postgresql/basis}. Beide Ordner behalten ihren Namen: Flyway merkt
 * sich je Installation, welche Skripte gelaufen sind.
 */
class FlywayMigrationenTest {

    private static final Path MIGRATIONEN = Path.of("src", "main", "resources", "db", "postgresql", "migration");
    private static final Path BASIS = Path.of("src", "main", "resources", "db", "postgresql", "basis", "V1__basis_schema.sql");
    /** Hier lagen frueher die MySQL-Migrationen - Flyway liest den Ordner nicht mehr. */
    private static final Path ALTE_MYSQL_ORDNER = Path.of("src", "main", "resources", "db", "migration");
    private static final Path ALTE_MYSQL_BASIS = Path.of("src", "main", "resources", "db", "basis");

    /** Alles bis hierhin steckt schon in der Basis (nach dem Neu-Erzeugen der Basis anheben). */
    static final int LETZTE_MIGRATION_IN_DER_BASIS = 406;

    /**
     * Checksumme jeder Migration, wie Flyway sie in {@code flyway_schema_history} der
     * laufenden Installationen gespeichert hat. Eine Migration, die auf main ist, darf sich
     * nie mehr aendern: Flyway prueft das wegen {@code validate-on-migrate=false} nicht, die
     * Installationen behielten den alten Stand, nur Neuinstallationen bekaemen den neuen.
     * Neue Migration: hier mit ihrer Checksumme eintragen (der Test nennt sie).
     */
    // Map.ofEntries statt Map.of: Map.of nimmt hoechstens zehn Paare.
    private static final Map<String, Integer> CHECKSUMMEN = Map.ofEntries(
            Map.entry("V407__email_postfaecher.sql", 1895864666),
            Map.entry("V408__email_postfach_sichtbarkeit.sql", 1105542612),
            Map.entry("V409__leere_versionen_auffuellen.sql", -113991001),
            Map.entry("V410__doppelte_fremdschluessel_entfernen.sql", 357620652),
            Map.entry("V411__schutzregeln_und_kunden_emails.sql", 1211966469),
            Map.entry("V412__datev_stundenart.sql", -328481948),
            Map.entry("V413__abwesenheitsnotiz_postfaecher.sql", -17262563),
            Map.entry("V414__benachrichtigung_erledigt.sql", 928601684),
            Map.entry("V415__freigabe_email_code.sql", 1251503045),
            Map.entry("V416__email_signatur_besitzer.sql", 1467540425),
            Map.entry("V417__telefon_reine_vorwahl_leeren.sql", -1484567923),
            Map.entry("V418__email_signatur_firmen_vorlage.sql", -1806692247),
            Map.entry("V419__email_gelesen_pro_benutzer.sql", -529857450),
            Map.entry("V420__anfrage_dokument_hochlader.sql", -787715335),
            Map.entry("V421__projekt_bezahlt_altfaelle_korrigieren.sql", -888475676),
            Map.entry("V422__lieferant_position_bezug.sql", -2011741806),
            Map.entry("V423__freigabe_rechtsnachweis.sql", 273844464),
            Map.entry("V424__rechtstext_fassung.sql", 157276652),
            Map.entry("V425__urlaubsantrag_entscheidung.sql", 1247692216),
            Map.entry("V426__abteilung_zeiterfassung_sichtbar.sql", -1951496007),
            Map.entry("V427__urlaub_uebertrag.sql", 483432275),
            Map.entry("V428__artikel_ean.sql", 368622790),
            Map.entry("V429__abteilung_darf_aufschlaege_aendern.sql", -1027148804),
            Map.entry("V430__kategorie_aufschlag.sql", 361623221));

    private static final char BOM = 0xFEFF;

    private static final Pattern DATEINAME = Pattern.compile("V(\\d++)__[A-Za-z0-9_]++\\.sql");

    private static final Pattern CHECK_ANFANG = Pattern.compile("(?i)\\bCHECK\\s*+\\(");
    private static final Pattern WERTELISTE = Pattern.compile("(?i)\\bIN\\s*+\\(");

    /**
     * CHECK mit Werteliste, aber bewusst: Die Spalte ist kein Java-Enum, sondern Text
     * mit festen Werten, die nur so in der Datenbank abgesichert sind.
     */
    private static final Set<String> ERLAUBTE_WERTELISTEN = Set.of(
            "V412__datev_stundenart.sql"); // stundenart: String "SOLL"/"IST"
    /** Dieselben CHECKs, wie sie pg_dump in eine neu erzeugte Basis schreibt. */
    private static final Set<String> ERLAUBTE_WERTELISTEN_CONSTRAINTS = Set.of(
            "ck_datev_konfiguration_stundenart", "ck_datev_personalnummer_stundenart"); // aus V412
    private static final Pattern CHECK_NAME = Pattern.compile("CONSTRAINT (\\w++) CHECK");

    /**
     * Enthaelt das SQL einen {@code CHECK (... IN (...))}? Geprueft wird die ganze
     * Bedingung bis zur passenden schliessenden Klammer - auch mit Klammern davor,
     * etwa {@code CHECK (upper(x) IN ('A'))}.
     */
    static boolean hatCheckMitWerteliste(String sql) {
        Matcher check = CHECK_ANFANG.matcher(sql);
        while (check.find()) {
            int tiefe = 1;
            int ende = check.end();
            while (ende < sql.length() && tiefe > 0) {
                char zeichen = sql.charAt(ende++);
                if (zeichen == '(') {
                    tiefe++;
                } else if (zeichen == ')') {
                    tiefe--;
                }
            }
            if (WERTELISTE.matcher(sql.substring(check.end(), ende)).find()) {
                return true;
            }
        }
        return false;
    }

    /** Segmente durch Punkte getrennt: possessiv und trotzdem treffend (ReDoS-sicher). */
    static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]++@[A-Za-z0-9-]++(?:\\.[A-Za-z0-9-]++)++");

    /** version, script, checksum aus den INSERTs von pg_dump --column-inserts */
    private static final Pattern HISTORIE = Pattern.compile(
            "INSERT INTO public\\.flyway_schema_history \\([^)]*+\\) VALUES \\(\\d++, '(\\d++)', '[^']*+', 'SQL', '(V\\d++__[A-Za-z0-9_]++\\.sql)', (-?\\d++),");

    private static final String REGEL = "(?: ON DELETE (CASCADE|SET NULL|RESTRICT|NO ACTION))?+";
    private static final Pattern ALTER_TABLE = Pattern.compile("^ALTER TABLE ONLY public\\.(\\w++)$");
    private static final Pattern FREMDSCHLUESSEL = Pattern.compile(
            "FOREIGN KEY \\(([^)]++)\\) REFERENCES public\\.(\\w++)\\(([^)]++)\\)" + REGEL);

    /** Versionsnummer -> Dateiname aller Migrationen. */
    private static Map<Integer, String> migrationen() throws IOException {
        Map<Integer, String> ergebnis = new TreeMap<>();
        try (Stream<Path> dateien = Files.list(MIGRATIONEN)) {
            for (Path datei : dateien.toList()) {
                Matcher treffer = DATEINAME.matcher(datei.getFileName().toString());
                if (treffer.matches()) {
                    String vorher = ergebnis.put(Integer.parseInt(treffer.group(1)), datei.getFileName().toString());
                    assertThat(vorher).as("Versionsnummer doppelt: %s", datei.getFileName()).isNull();
                }
            }
        }
        return ergebnis;
    }

    /** Nachbau von Flyways ChecksumCalculator (CRC32 ueber Zeilen ohne Umbruch, BOM entfernt). */
    static int flywayChecksumme(Path datei) throws IOException {
        CRC32 crc = new CRC32();
        try (BufferedReader leser = Files.newBufferedReader(datei, StandardCharsets.UTF_8)) {
            String zeile = leser.readLine();
            if (zeile != null && !zeile.isEmpty() && zeile.charAt(0) == BOM) {
                zeile = zeile.substring(1);
            }
            while (zeile != null) {
                crc.update(zeile.getBytes(StandardCharsets.UTF_8));
                zeile = leser.readLine();
            }
        }
        return (int) crc.getValue();
    }

    @Nested
    @DisplayName("Migrationen")
    class Migrationen {

        @Test
        @DisplayName("Keine MySQL-Migrationen mehr: neue Skripte nur unter db/postgresql/migration")
        void keineMysqlLinie() throws IOException {
            for (Path ordner : List.of(ALTE_MYSQL_ORDNER, ALTE_MYSQL_BASIS)) {
                if (Files.isDirectory(ordner)) {
                    try (Stream<Path> dateien = Files.walk(ordner)) {
                        assertThat(dateien.filter(Files::isRegularFile).toList())
                                .as("%s wird von Flyway nicht gelesen - Migration nach %s verschieben", ordner, MIGRATIONEN)
                                .isEmpty();
                    }
                }
            }
        }

        @Test
        @DisplayName("Bestehende Migrationen sind unveraendert (Checksumme wie in den Installationen)")
        void bestehendeMigrationenUnveraendert() throws IOException {
            for (String datei : migrationen().values()) {
                int ist = flywayChecksumme(MIGRATIONEN.resolve(datei));
                assertThat(CHECKSUMMEN.get(datei))
                        .as("%s fehlt in CHECKSUMMEN - als neue Migration eintragen: \"%s\", %d", datei, datei, ist)
                        .isNotNull();
                assertThat(ist)
                        .as("%s wurde geaendert. Bestehende Migrationen NIE aendern - neue Migration anlegen. "
                                + "(Nur wenn sie noch NICHT auf main ist: Checksumme %d eintragen.)", datei, ist)
                        .isEqualTo(CHECKSUMMEN.get(datei));
            }
            assertThat(migrationen().values()).as("Eintrag ohne Datei in CHECKSUMMEN")
                    .containsAll(CHECKSUMMEN.keySet());
        }

        @Test
        @DisplayName("Jede Datei im Migrationsordner hat einen Namen, den Flyway erkennt")
        void dateinamenGueltig() throws IOException {
            try (Stream<Path> dateien = Files.list(MIGRATIONEN)) {
                assertThat(dateien.map(datei -> datei.getFileName().toString())
                        .filter(name -> !"README.md".equals(name)))
                        .as("V{Nummer}__{beschreibung}.sql - sonst ueberspringt Flyway die Datei still")
                        .allMatch(name -> DATEINAME.matcher(name).matches());
            }
        }

        @Test
        @DisplayName("Migrationen beginnen erst nach der Basis")
        void keineAltenNummern() throws IOException {
            assertThat(migrationen().keySet())
                    .isNotEmpty()
                    .allMatch(version -> version > LETZTE_MIGRATION_IN_DER_BASIS);
        }

        @Test
        @DisplayName("Keine Enum-CHECKs mit Werteliste - neue Enum-Werte sollen keine Migration brauchen")
        void keineEnumChecks() throws IOException {
            for (String datei : migrationen().values()) {
                if (ERLAUBTE_WERTELISTEN.contains(datei)) {
                    continue;
                }
                assertThat(hatCheckMitWerteliste(Files.readString(MIGRATIONEN.resolve(datei), StandardCharsets.UTF_8)))
                        .as("%s: CHECK mit Werteliste - Enum-Werte nicht in der Datenbank festschreiben", datei)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("Die Werteliste-Pruefung schlaegt wirklich an (sonst waere der Test oben wertlos)")
        void wertelistePruefungSchlaegtAn() {
            assertThat(hatCheckMitWerteliste("ADD CONSTRAINT ck CHECK (status IN ('OFFEN', 'ERLEDIGT'));")).isTrue();
            assertThat(hatCheckMitWerteliste("add constraint ck check(typ in('A'))")).isTrue();
            assertThat(hatCheckMitWerteliste("ADD CONSTRAINT ck CHECK (betrag >= 0); -- admin IN (x)")).isFalse();
            assertThat(hatCheckMitWerteliste("CREATE INDEX idx_admin ON nutzer (admin_in)")).isFalse();
            assertThat(hatCheckMitWerteliste("CHECK (upper(art) IN ('A', 'B'))")).isTrue();
            assertThat(hatCheckMitWerteliste("CHECK ((art)::text IN ('A'))")).isTrue();
        }

        @Test
        @DisplayName("Erlaubte Wertelisten gibt es wirklich (keine Ausnahme fuer geloeschte Dateien)")
        void ausnahmenAktuell() throws IOException {
            assertThat(migrationen().values()).containsAll(ERLAUBTE_WERTELISTEN);
            for (String datei : ERLAUBTE_WERTELISTEN) {
                assertThat(hatCheckMitWerteliste(Files.readString(MIGRATIONEN.resolve(datei), StandardCharsets.UTF_8)))
                        .as("%s braucht keine Ausnahme mehr", datei).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Basis-Datei")
    class Basis {

        @Test
        @DisplayName("Basis ist vorhanden und enthaelt das komplette Schema")
        void vollstaendig() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            assertThat(Pattern.compile("(?m)^CREATE TABLE ").matcher(basis).results().count())
                    .isGreaterThan(100);
            assertThat(basis).contains("PostgreSQL database dump complete");
        }

        @Test
        @DisplayName("Basis enthaelt keine E-Mail-Adressen (keine Personendaten)")
        void keinePersonendaten() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            assertThat(EMAIL.matcher(basis).find()).as("E-Mail-Adresse im Basis-Schema").isFalse();
        }

        @Test
        @DisplayName("Das E-Mail-Muster erkennt Adressen wirklich (sonst waere der Test oben wertlos)")
        void emailMusterSchlaegtAn() {
            assertThat(EMAIL.matcher("INSERT ... 'max.mustermann@example.com' ...").find()).isTrue();
            assertThat(EMAIL.matcher("a@b.co").find()).isTrue();
            assertThat(EMAIL.matcher("kein at-zeichen.de").find()).isFalse();
        }

        @Test
        @DisplayName("Basis loescht nichts und verstellt die Verbindung nicht")
        void keinDropKeinPsqlBefehl() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            assertThat(basis).doesNotContainPattern("(?i)\\bDROP\\s++(TABLE|DATABASE|SCHEMA)\\b");
            // psql-Befehle (\restrict ...) versteht Flyway nicht; ein geleerter
            // search_path wuerde die Verbindung fuer die App unbrauchbar machen
            assertThat(basis).doesNotContainPattern("(?m)^\\\\");
            assertThat(basis).doesNotContain("set_config('search_path'");
        }

        @Test
        @DisplayName("Keine Enum-CHECKs in der Basis")
        void keineEnumChecks() throws IOException {
            // pg_dump schreibt "x IN ('A','B')" als "x = ANY ((ARRAY['A', 'B']))"
            for (String zeile : Files.readAllLines(BASIS, StandardCharsets.UTF_8)) {
                if (zeile.contains("= ANY ((ARRAY[")) {
                    Matcher name = CHECK_NAME.matcher(zeile);
                    assertThat(name.find() && ERLAUBTE_WERTELISTEN_CONSTRAINTS.contains(name.group(1)))
                            .as("CHECK mit Werteliste in der Basis: %s", zeile.strip())
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("Jede Migration der Basis-Historie existiert unveraendert (Checksumme wie Flyway)")
        void historiePasstZuDenMigrationen() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            Matcher treffer = HISTORIE.matcher(basis);
            while (treffer.find()) {
                Path datei = MIGRATIONEN.resolve(treffer.group(2));
                assertThat(datei).as("Migration aus der Basis-Historie fehlt").exists();
                assertThat(flywayChecksumme(datei))
                        .as("%s wurde nach dem Erzeugen der Basis geaendert - bestehende Migrationen nie aendern", datei)
                        .isEqualTo(Integer.parseInt(treffer.group(3)));
            }
        }

        @Test
        @DisplayName("Das Historien-Muster passt zur Ausgabe von pg_dump (sonst waere der Test oben wertlos)")
        void historienMusterSchlaegtAn() {
            String zeile = "INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, "
                    + "checksum, installed_by, installed_on, execution_time, success) VALUES "
                    + "(1, '407', 'beispiel', 'SQL', 'V407__beispiel.sql', -12345, 'basis-schema', '2026-01-01 00:00:00', 0, true);";
            Matcher treffer = HISTORIE.matcher(zeile);
            assertThat(treffer.find()).isTrue();
            assertThat(treffer.group(2)).isEqualTo("V407__beispiel.sql");
            assertThat(treffer.group(3)).isEqualTo("-12345");
        }

        @Test
        @DisplayName("Fremdschluessel mit Loeschregeln (CASCADE / SET NULL) sind in der Basis")
        void fremdschluesselMitLoeschregeln() throws IOException {
            Set<String> regeln = new TreeSet<>();
            List<String> fremdschluessel = new ArrayList<>();
            String tabelle = null;
            for (String zeile : Files.readAllLines(BASIS, StandardCharsets.UTF_8)) {
                Matcher alter = ALTER_TABLE.matcher(zeile);
                if (alter.find()) {
                    tabelle = alter.group(1);
                    continue;
                }
                Matcher fk = FREMDSCHLUESSEL.matcher(zeile);
                if (fk.find()) {
                    fremdschluessel.add(tabelle + "(" + fk.group(1) + ")->" + fk.group(2));
                    regeln.add(fk.group(4) == null ? "NO ACTION" : fk.group(4));
                }
            }
            // Eine Basis ohne Fremdschluessel (z. B. falsch erzeugt) liesse Loeschen
            // von Datensaetzen zu, auf die noch etwas verweist
            assertThat(fremdschluessel).hasSizeGreaterThan(100);
            assertThat(regeln).contains("CASCADE", "SET NULL", "NO ACTION");
        }
    }
}
