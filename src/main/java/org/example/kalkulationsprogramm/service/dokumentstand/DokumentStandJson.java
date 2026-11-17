package org.example.kalkulationsprogramm.service.dokumentstand;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Liest und schreibt die JSON-Spalten eines Stands ({@code aenderungen}: Sätze,
 * {@code bearbeitet_von_ids}: Benutzer-IDs). Kaputte oder leere Werte ergeben eine leere Liste.
 */
final class DokumentStandJson {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private DokumentStandJson() {
    }

    static String schreibeSaetze(List<String> saetze) {
        return MAPPER.writeValueAsString(saetze == null ? List.of() : saetze);
    }

    static String schreibeIds(List<Long> ids) {
        return MAPPER.writeValueAsString(ids == null ? List.of() : ids);
    }

    static List<String> leseSaetze(String json) {
        List<String> saetze = new ArrayList<>();
        for (JsonNode n : array(json)) {
            if (n.isString()) {
                saetze.add(n.asString(""));
            }
        }
        return saetze;
    }

    static List<Long> leseIds(String json) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : array(json)) {
            if (n.isIntegralNumber()) {
                ids.add(n.asLong(0L));
            }
        }
        return ids;
    }

    private static Iterable<JsonNode> array(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.isArray() ? node : List.of();
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
