package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class DatenbankArtTest {

    /** Datenbank, deren JDBC-Treiber sich mit {@code produkt} meldet. */
    static DataSource dataSourceMitProdukt(String produkt) throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection verbindung = Mockito.mock(Connection.class);
        DatabaseMetaData metadaten = Mockito.mock(DatabaseMetaData.class);
        given(dataSource.getConnection()).willReturn(verbindung);
        given(verbindung.getMetaData()).willReturn(metadaten);
        given(metadaten.getDatabaseProductName()).willReturn(produkt);
        return dataSource;
    }

    @ParameterizedTest(name = "{0} -> postgres={1}")
    @CsvSource({ "PostgreSQL, true", "MySQL, false", "H2, false", "MariaDB, false" })
    @DisplayName("Erkennt PostgreSQL am Produktnamen des Treibers")
    void erkenntDatenbank(String produkt, boolean postgres) throws SQLException {
        assertThat(DatenbankArt.istPostgres(dataSourceMitProdukt(produkt))).isEqualTo(postgres);
        assertThat(new DatenbankArt(dataSourceMitProdukt(produkt)).istPostgres()).isEqualTo(postgres);
    }

    @Test
    @DisplayName("Treiber ohne Produktnamen: kein Absturz, gilt nicht als PostgreSQL")
    void ohneProduktname() throws SQLException {
        assertThat(DatenbankArt.istPostgres(dataSourceMitProdukt(null))).isFalse();
    }

    @Test
    @DisplayName("Keine Verbindung: Start bricht mit klarer Meldung ab")
    void keineVerbindung() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        given(dataSource.getConnection()).willThrow(new SQLException("keine Verbindung"));
        assertThatThrownBy(() -> DatenbankArt.istPostgres(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Datenbankart");
    }

    @Test
    @DisplayName("SchemaFix (MySQL-Altlast) fasst PostgreSQL nicht an")
    void schemaFixUeberspringtPostgres() throws Exception {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        CommandLineRunner fix = new SchemaFixConfig().schemaFixer(jdbc, DatenbankArt.fuerTest(true));

        fix.run();

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("SchemaFix laeuft auf MySQL weiter wie bisher")
    void schemaFixAufMysql() throws Exception {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        CommandLineRunner fix = new SchemaFixConfig().schemaFixer(jdbc, DatenbankArt.fuerTest(false));

        fix.run();

        Mockito.verify(jdbc).execute(Mockito.contains("bestellung_projekt_zuordnung"));
    }

    @Test
    @DisplayName("Neuinstallation: PostgreSQL und MySQL bekommen je ihre eigene Basis")
    void basisJeDatenbank() throws SQLException {
        assertThat(FlywayStartSetupConfig.basisOrt(dataSourceMitProdukt("PostgreSQL")))
                .isEqualTo("classpath:db/postgresql/basis");
        assertThat(FlywayStartSetupConfig.basisOrt(dataSourceMitProdukt("MySQL")))
                .isEqualTo("classpath:db/basis");
    }
}
