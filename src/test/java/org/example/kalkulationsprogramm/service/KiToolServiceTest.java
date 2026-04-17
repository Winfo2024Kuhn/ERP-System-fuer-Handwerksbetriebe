package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@ExtendWith(MockitoExtension.class)
class KiToolServiceTest {

    @Mock private LocalRagService localRagService;
    @Mock private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KiToolService service;

    @BeforeEach
    void setUp() {
        service = new KiToolService(localRagService, jdbcTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "pythonEnabled", true);
        ReflectionTestUtils.setField(service, "pythonCommand", "py");
    }

    @Nested
    class BuildFunctionDeclarations {

        @Test
        void shouldIncludeRunPythonDeclarationWhenEnabled() {
            ArrayNode declarations = service.buildFunctionDeclarations();

            assertThat(declarations).hasSize(6);
            assertThat(declarations.get(5).path("name").asText()).isEqualTo("run_python");
            assertThat(declarations.get(5).path("parameters").path("required")).containsExactly(objectMapper.getNodeFactory().textNode("code"));
        }

        @Test
        void shouldExcludeRunPythonWhenDisabled() {
            ReflectionTestUtils.setField(service, "pythonEnabled", false);

            ArrayNode declarations = service.buildFunctionDeclarations();

            assertThat(declarations).hasSize(5);
            assertThat(new ArrayList<>(declarations.findValuesAsText("name"))).doesNotContain("run_python");
        }
    }

    @Nested
    class ExecuteTool {

        @Test
        void shouldReturnErrorForUnknownTool() {
            JsonNode args = objectMapper.createObjectNode();

            String result = service.executeTool("unknown_tool", args);

            assertThat(result).isEqualTo("Unbekanntes Tool: unknown_tool");
        }

        @Test
        void shouldDelegateToSearchCode() throws Exception {
            when(localRagService.isAvailable()).thenReturn(true);
            when(localRagService.search(eq("Projekt"), any(), any())).thenReturn(List.of());

            JsonNode args = objectMapper.createObjectNode().put("query", "Projekt");
            String result = service.executeTool("search_code", args);

            assertThat(result).contains("Keine relevanten Code-Abschnitte");
        }
    }

    @Nested
    class ReadFile {

        @Test
        void shouldRejectPathTraversal() {
            JsonNode args = objectMapper.createObjectNode().put("path", "../../etc/passwd");

            String result = service.executeTool("read_file", args);

            assertThat(result).contains("Zugriff verweigert");
        }

        @Test
        void shouldReadExistingProjectFile() {
            JsonNode args = objectMapper.createObjectNode().put("path", "mvnw.cmd");

            String result = service.executeTool("read_file", args);

            assertThat(result).contains("MAVEN");
        }
    }

    @Nested
    class QueryDatabase {

        @Test
        void shouldRejectNonSelectStatements() {
            JsonNode args = objectMapper.createObjectNode().put("sql", "UPDATE users SET name='hack'");

            String result = service.executeTool("query_database", args);

            assertThat(result).contains("Nur SELECT-Statements sind erlaubt");
        }

        @Test
        void shouldRejectDangerousSelectFunctions() {
            JsonNode args = objectMapper.createObjectNode().put("sql", "SELECT SLEEP(10)");

            String result = service.executeTool("query_database", args);

            assertThat(result).contains("Sicherheitsgruenden nicht erlaubt");
        }

        @Test
        void shouldRejectMultipleStatements() {
            JsonNode args = objectMapper.createObjectNode().put("sql", "SELECT 1; DROP TABLE users; --");

            String result = service.executeTool("query_database", args);

            assertThat(result).contains("Mehrere SQL-Statements sind nicht erlaubt");
        }

        @Test
        void shouldRejectSqlComments() {
            JsonNode args = objectMapper.createObjectNode().put("sql", "SELECT 1 /* malicious */");

            String result = service.executeTool("query_database", args);

            assertThat(result).contains("Sicherheitsgruenden nicht erlaubt");
        }

        @Test
        void shouldRejectBenchmark() {
            JsonNode args = objectMapper.createObjectNode().put("sql", "SELECT BENCHMARK(10000000, SHA1('test'))");

            String result = service.executeTool("query_database", args);

            assertThat(result).contains("Sicherheitsgruenden nicht erlaubt");
        }

        @Test
        void shouldExecuteValidSelect() {
            when(jdbcTemplate.queryForList("SELECT 1 AS val")).thenReturn(List.of(Map.of("val", 1)));

            JsonNode args = objectMapper.createObjectNode().put("sql", "SELECT 1 AS val");
            String result = service.executeTool("query_database", args);

            assertThat(result).contains("val");
            assertThat(result).contains("1");
        }
    }

    @Nested
    class RunPython {

        @Test
        void shouldReturnDisabledMessageWhenPythonIsOff() {
            ReflectionTestUtils.setField(service, "pythonEnabled", false);
            JsonNode args = objectMapper.createObjectNode().put("code", "print(1)");

            String result = service.executeTool("run_python", args);

            assertThat(result).isEqualTo("Python-Ausfuehrung ist deaktiviert.");
        }

        @Test
        void shouldRejectDangerousPythonCode() {
            JsonNode args = objectMapper.createObjectNode().put("code", "import os\nos.system('dir')");

            String result = service.executeTool("run_python", args);

            assertThat(result).contains("Sicherheitsfehler");
        }

        @Test
        void shouldRejectBlankPythonCode() {
            JsonNode args = objectMapper.createObjectNode().put("code", "");

            String result = service.executeTool("run_python", args);

            assertThat(result).isEqualTo("Kein Python-Code angegeben.");
        }
    }
}
