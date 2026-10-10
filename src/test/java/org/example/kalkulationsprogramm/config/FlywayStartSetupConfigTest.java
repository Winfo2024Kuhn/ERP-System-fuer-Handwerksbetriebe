package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class FlywayStartSetupConfigTest {

    /** PostgreSQL-Datenbank, deren JDBC-Metadaten {@code anzahl} Tabellen melden. */
    static DataSource dataSourceMitTabellen(long anzahl) throws SQLException {
        return dataSource("PostgreSQL", anzahl);
    }

    private static DataSource dataSource(String produkt, long anzahl) throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection verbindung = Mockito.mock(Connection.class);
        DatabaseMetaData metadaten = Mockito.mock(DatabaseMetaData.class);
        ResultSet tabellen = Mockito.mock(ResultSet.class);
        given(dataSource.getConnection()).willReturn(verbindung);
        given(verbindung.getMetaData()).willReturn(metadaten);
        given(verbindung.getCatalog()).willReturn("kalkulationsprogramm_db");
        given(metadaten.getDatabaseProductName()).willReturn(produkt);
        given(metadaten.getTables(any(), any(), anyString(), any())).willReturn(tabellen);
        given(tabellen.next()).willReturn(anzahl > 0);
        return dataSource;
    }

    private static Flyway flywayMit(DataSource dataSource) {
        Flyway flyway = Mockito.mock(Flyway.class);
        Configuration konfiguration = Mockito.mock(Configuration.class);
        given(flyway.getConfiguration()).willReturn(konfiguration);
        given(konfiguration.getDataSource()).willReturn(dataSource);
        return flyway;
    }

    @Nested
    @DisplayName("istLeer")
    class IstLeer {

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
    @DisplayName("Nur PostgreSQL")
    class NurPostgres {

        @Test
        @DisplayName("PostgreSQL wird angenommen")
        void postgresOk() throws SQLException {
            assertThatCode(() -> FlywayStartSetupConfig.pruefePostgres(dataSourceMitTabellen(0)))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = { "MySQL", "MariaDB", "H2" })
        @DisplayName("Andere Datenbank: klare Meldung statt halb migriertem Schema")
        void andereDatenbankAbgelehnt(String produkt) throws SQLException {
            assertThatThrownBy(() -> FlywayStartSetupConfig.pruefePostgres(dataSource(produkt, 0)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nur noch mit PostgreSQL")
                    .hasMessageContaining(produkt);
        }

        @Test
        @DisplayName("Unbekannter Produktname wird abgelehnt")
        void ohneProduktname() throws SQLException {
            assertThatThrownBy(() -> FlywayStartSetupConfig.pruefePostgres(dataSource(null, 0)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Keine Verbindung: Start bricht ab")
        void verbindungsfehler() throws SQLException {
            DataSource dataSource = Mockito.mock(DataSource.class);
            given(dataSource.getConnection()).willThrow(new SQLException("keine Verbindung"));
            assertThatThrownBy(() -> FlywayStartSetupConfig.pruefePostgres(dataSource))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Datenbankart");
        }
    }

    @Nested
    @DisplayName("Strategie")
    class Strategie {

        @Test
        @DisplayName("Bestehende Datenbank: nur normales migrate(), keine Basis")
        void bestehendeDatenbankNurMigrate() throws SQLException {
            Flyway flyway = flywayMit(dataSourceMitTabellen(139));

            new FlywayStartSetupConfig().flywayMigrationStrategy().migrate(flyway);

            Mockito.verify(flyway).migrate();
            // Nur fuer die Leer-Pruefung; der Basis-Pfad braeuchte die Konfiguration ein zweites Mal
            Mockito.verify(flyway, Mockito.times(1)).getConfiguration();
        }

        @Test
        @DisplayName("Vergessene MySQL-Datenbank: Flyway laeuft gar nicht erst los")
        void mysqlWirdNichtMigriert() throws SQLException {
            Flyway flyway = flywayMit(dataSource("MySQL", 139));

            assertThatThrownBy(() -> new FlywayStartSetupConfig().flywayMigrationStrategy().migrate(flyway))
                    .isInstanceOf(IllegalStateException.class);
            Mockito.verify(flyway, Mockito.never()).migrate();
        }
    }
}
