package org.example.kalkulationsprogramm.util;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Liest Leistungs-Positionen aus dem {@code positionenJson} eines Ausgangsdokuments.
 * Berücksichtigt SERVICE-Blöcke auf oberster Ebene und in SECTION_HEADER-Containern.
 */
@Slf4j
public final class LeistungsPositionen {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private LeistungsPositionen() {
    }

    /** Alle Leistungs-IDs der Positionen, auch optionale. */
    public static Set<Long> leistungIds(String positionenJson) {
        Set<Long> ids = new HashSet<>();
        for (JsonNode block : bloecke(positionenJson)) {
            sammleIds(block, ids);
        }
        return ids;
    }

    /**
     * Summiert die Menge je Leistung (optionale Positionen zählen nicht) und merkt sich die
     * Kategorie, die beim Einfügen in der Position gespeichert wurde. Sie hilft, wenn die
     * Leistung inzwischen gelöscht ist.
     */
    public static void sammleMengen(String positionenJson, Map<Long, BigDecimal> mengen,
            Map<Long, Long> kategorieJeLeistung) {
        for (JsonNode block : bloecke(positionenJson)) {
            sammleMengen(block, mengen, kategorieJeLeistung);
        }
    }

    private static Iterable<JsonNode> bloecke(String positionenJson) {
        if (positionenJson == null || positionenJson.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = MAPPER.readTree(positionenJson);
            if (root.isArray()) {
                return root;
            }
            if (root.has("blocks") && root.get("blocks").isArray()) {
                return root.get("blocks");
            }
        } catch (Exception e) {
            log.warn("Positionen konnten nicht gelesen werden: {}", e.getMessage());
        }
        return Set.of();
    }

    private static boolean istLeistung(JsonNode block) {
        return "SERVICE".equals(block.path("type").asString(""))
                && block.has("leistungId") && !block.get("leistungId").isNull();
    }

    private static Iterable<JsonNode> kinder(JsonNode block) {
        if ("SECTION_HEADER".equals(block.path("type").asString("")) && block.path("children").isArray()) {
            return block.get("children");
        }
        return Set.of();
    }

    private static void sammleIds(JsonNode block, Set<Long> ids) {
        if (istLeistung(block)) {
            ids.add(block.get("leistungId").asLong(0L));
        }
        for (JsonNode kind : kinder(block)) {
            sammleIds(kind, ids);
        }
    }

    private static void sammleMengen(JsonNode block, Map<Long, BigDecimal> mengen,
            Map<Long, Long> kategorieJeLeistung) {
        if (istLeistung(block) && !block.path("optional").asBoolean(false)) {
            long leistungId = block.get("leistungId").asLong(0L);
            BigDecimal menge = block.has("quantity") && !block.get("quantity").isNull()
                    ? BigDecimal.valueOf(block.get("quantity").asDouble(0.0))
                    : BigDecimal.ZERO;
            mengen.merge(leistungId, menge, BigDecimal::add);
            if (block.has("kategorieId") && !block.get("kategorieId").isNull()) {
                kategorieJeLeistung.put(leistungId, block.get("kategorieId").asLong(0L));
            }
        }
        for (JsonNode kind : kinder(block)) {
            sammleMengen(kind, mengen, kategorieJeLeistung);
        }
    }
}
