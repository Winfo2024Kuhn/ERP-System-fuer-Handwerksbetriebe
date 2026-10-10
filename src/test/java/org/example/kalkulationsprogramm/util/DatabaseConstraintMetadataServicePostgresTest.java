package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;

class DatabaseConstraintMetadataServicePostgresTest {

    @Test
    @DisplayName("PostgreSQL: current_schema() und constraint_column_usage")
    void postgresAbfragen() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        given(jdbc.queryForObject(anyString(), eq(String.class))).willReturn("public");

        DatabaseConstraintMetadataService dienst = new DatabaseConstraintMetadataService(jdbc);

        assertThat(dienst.getSchemaName()).isEqualTo("public");
        verify(jdbc).queryForObject("SELECT current_schema()", String.class);
        verify(jdbc).query(contains("constraint_column_usage"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        // Keine Kommentar-Spalten in PostgreSQLs information_schema - Labels aus den Namen
        verify(jdbc).query(contains("table_name AS table_comment"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
        verify(jdbc).query(contains("column_name AS column_comment"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
    }
}
