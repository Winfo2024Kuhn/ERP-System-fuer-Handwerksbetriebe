package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FlywayStartSetupConfigTest {

    private static final Path BASIS = Path.of("src", "main", "resources", "db", "basis", "V1__basis_schema.sql");
    private static final Path MIGRATIONEN = Path.of("src", "main", "resources", "db", "migration");

    /** Datenbank, deren JDBC-Metadaten {@code anzahl} Tabellen melden. */
    static DataSource dataSourceMitTabellen(long anzahl) throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection verbindung = Mockito.mock(Connection.class);
        DatabaseMetaData metadaten = Mockito.mock(DatabaseMetaData.class);
        ResultSet tabellen = Mockito.mock(ResultSet.class);
        given(dataSource.getConnection()).willReturn(verbindung);
        given(verbindung.getMetaData()).willReturn(metadaten);
        given(verbindung.getCatalog()).willReturn("kalkulationsprogramm_db");
        given(metadaten.getTables(any(), any(), anyString(), any())).willReturn(tabellen);
        given(tabellen.next()).willReturn(anzahl > 0);
        return dataSource;
    }

    @Nested
    @DisplayName("istLeer")
    class IstLeer {

        private DataSource dataSourceMitTabellen(long anzahl) throws SQLException {
            return FlywayStartSetupConfigTest.dataSourceMitTabellen(anzahl);
        }

        @Test
        @DisplayName("Keine Tabelle: Neuinstallation, Basis wird eingespielt")
        void ohneTabellenLeer() throws SQLException {
            assertThat(FlywayStartSetupConfig.istLeer(dataSourceMitTabellen(0))).isTrue();
        }

        @Test
        @DisplayName("Bestehende Datenbank bleibt unberuehrt")
        void mitTabellenNichtLeer() throws SQLException {
            assertThat(FlywayStartSetupConfig.istLeer(dataSourceMitTabellen(139))).isFalse();
        }

        @Test
        @DisplayName("Datenbankfehler bricht den Start ab statt still weiterzumachen")
        void datenbankfehler() throws SQLException {
            DataSource dataSource = Mockito.mock(DataSource.class);
            given(dataSource.getConnection()).willThrow(new SQLException("keine Verbindung"));
            assertThatThrownBy(() -> FlywayStartSetupConfig.istLeer(dataSource))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Strategie")
    class Strategie {

        @Test
        @DisplayName("Bestehende Datenbank: nur normales migrate(), keine Basis")
        void bestehendeDatenbankNurMigrate() throws SQLException {
            DataSource dataSource = dataSourceMitTabellen(139);

            Flyway flyway = Mockito.mock(Flyway.class);
            Configuration konfiguration = Mockito.mock(Configuration.class);
            given(flyway.getConfiguration()).willReturn(konfiguration);
            given(konfiguration.getDataSource()).willReturn(dataSource);

            new FlywayStartSetupConfig().flywayMigrationStrategy().migrate(flyway);

            Mockito.verify(flyway).migrate();
            // Nur fuer die Leer-Pruefung; der Basis-Pfad braeuchte die Konfiguration ein zweites Mal
            Mockito.verify(flyway, Mockito.times(1)).getConfiguration();
        }
    }

    @Nested
    @DisplayName("Basis-Schema-Datei")
    class BasisSchema {

        /** Segmente durch Punkte getrennt: possessiv und trotzdem treffend (ReDoS-sicher). */
        static final Pattern EMAIL = Pattern.compile(
                "[A-Za-z0-9._%+-]++@[A-Za-z0-9-]++(?:\\.[A-Za-z0-9-]++)++");

        /** version, script, checksum aus den INSERTs in flyway_schema_history */
        private static final Pattern HISTORIE = Pattern.compile(
                "\\(\\d++,'(\\d++)','[^']*+','SQL','(V\\d++__[A-Za-z0-9_]++\\.sql)',(-?\\d++),");

        @Test
        @DisplayName("Jede Migration der Basis-Historie existiert unveraendert (Checksumme wie Flyway)")
        void historiePasstZuDenMigrationen() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            Matcher treffer = HISTORIE.matcher(basis);
            List<String> geprueft = new ArrayList<>();
            while (treffer.find()) {
                Path datei = MIGRATIONEN.resolve(treffer.group(2));
                assertThat(datei).as("Migration aus der Basis-Historie fehlt").exists();
                assertThat(flywayChecksumme(datei))
                        .as("%s wurde nach dem Erzeugen der Basis geaendert - bestehende Migrationen nie aendern", datei)
                        .isEqualTo(Integer.parseInt(treffer.group(3)));
                geprueft.add(treffer.group(1));
            }
            assertThat(geprueft).as("Basis enthaelt keine Flyway-Historie").hasSizeGreaterThan(100);
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
        @DisplayName("Basis loescht nichts (kein DROP) - sie laeuft nur auf leeren Datenbanken, aber sicher ist sicher")
        void keinDrop() throws IOException {
            String basis = Files.readString(BASIS, StandardCharsets.UTF_8);
            assertThat(basis).doesNotContainPattern("(?i)\\bDROP\\s++(TABLE|DATABASE|SCHEMA)\\b");
        }

        /** Nachbau von Flyways ChecksumCalculator (CRC32 ueber Zeilen ohne Umbruch, BOM entfernt). */
        static int flywayChecksumme(Path datei) throws IOException {
            CRC32 crc = new CRC32();
            try (BufferedReader leser = Files.newBufferedReader(datei, StandardCharsets.UTF_8)) {
                String zeile = leser.readLine();
                if (zeile != null && zeile.startsWith("﻿")) {
                    zeile = zeile.substring(1);
                }
                while (zeile != null) {
                    crc.update(zeile.getBytes(StandardCharsets.UTF_8));
                    zeile = leser.readLine();
                }
            }
            return (int) crc.getValue();
        }
    }
}
