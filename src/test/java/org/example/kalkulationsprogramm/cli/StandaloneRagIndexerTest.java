package org.example.kalkulationsprogramm.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class StandaloneRagIndexerTest {

    private final StandaloneRagIndexer indexer = new StandaloneRagIndexer("dummy-key", ".");

    @Test
    @DisplayName("splitJavaMethods trennt Methoden mit und ohne Annotationen korrekt auf")
    void splitJavaMethods_trenntMethoden() {
        String javaCode = """
                package com.example;

                public class SampleService {
                    private final String name;

                    public SampleService(String name) {
                        this.name = name;
                    }

                    @Override
                    @Deprecated
                    public String toString() {
                        return name;
                    }

                    public void doSomething() {
                        System.out.println(name);
                    }

                    List<String> buildKetten(int id) {
                        return List.of();
                    }
                }
                """;

        List<String> methods = indexer.splitJavaMethods(javaCode);
        assertThat(methods).hasSize(5);
        assertThat(methods.get(0)).contains("name;");
        assertThat(methods.get(1)).startsWith("public SampleService");
        assertThat(methods.get(2)).startsWith("@Override");
        assertThat(methods.get(3)).startsWith("public void doSomething");
        assertThat(methods.get(4)).startsWith("List<String> buildKetten");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @DisplayName("splitJavaMethods ist immun gegen ReDoS (Catastrophic Backtracking)")
    void splitJavaMethods_reDosResilience() {
        // Pathologische Eingabe mit vielen Wiederholungen von "@a\\n" und "    "
        StringBuilder pathological = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            pathological.append("    @annotation").append(i).append(" trailing stuff\n");
        }
        pathological.append("    not_a_method_modifier_so_it_fails_match();\n");

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            List<String> result = indexer.splitJavaMethods(pathological.toString());
            assertThat(result).isNotEmpty();
        });
    }
}
