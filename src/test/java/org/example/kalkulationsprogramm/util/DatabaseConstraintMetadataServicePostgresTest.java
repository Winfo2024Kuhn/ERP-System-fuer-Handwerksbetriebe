package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.kalkulationsprogramm.config.DatenbankArt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;

class DatabaseConstraintMetadataServicePostgresTest {

    @Test
    @DisplayName("PostgreSQL: eigene Abfragen (current_schema, constraint_column_usage) statt MySQL-Syntax")
    void postgresNutztEigeneAbfragen() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        given(jdbc.queryForObject(anyString(), eq(String.class))).willReturn("public");

        DatabaseConstraintMetadataService dienst = new DatabaseConstraintMetadataService(jdbc, DatenbankArt.fuerTest(true));

        assertThat(dienst.getSchemaName()).isEqualTo("public");
        verify(jdbc).queryForObject("SELECT current_schema()", String.class);
        verify(jdbc, never()).queryForObject(eq("SELECT DATABASE()"), eq(String.class));
        verify(jdbc).query(contains("constraint_column_usage"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        // Keine Kommentar-Spalten in PostgreSQLs information_schema - Labels aus den Namen
        verify(jdbc).query(contains("table_name AS table_comment"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
        verify(jdbc, never()).query(contains("IFNULL"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
    }

    @Test
    @DisplayName("MySQL bleibt bei DATABASE() und den Kommentar-Spalten")
    void mysqlUnveraendert() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        given(jdbc.queryForObject(anyString(), eq(String.class))).willReturn("kalkulationsprogramm_db");

        DatabaseConstraintMetadataService dienst = new DatabaseConstraintMetadataService(jdbc, DatenbankArt.fuerTest(false));

        assertThat(dienst.getSchemaName()).isEqualTo("kalkulationsprogramm_db");
        verify(jdbc).queryForObject("SELECT DATABASE()", String.class);
        verify(jdbc).query(contains("referential_constraints"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
    }
}
