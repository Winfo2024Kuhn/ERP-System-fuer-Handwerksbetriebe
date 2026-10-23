package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LeistungsPositionenTest {

    private static final String POSITIONEN = """
            [{"type":"SERVICE","leistungId":1,"kategorieId":10,"quantity":2},
             {"type":"TEXT","content":"Hinweis"},
             {"type":"SERVICE","leistungId":null,"quantity":9},
             {"type":"SERVICE","title":"freie Position","quantity":9},
             {"type":"SECTION_HEADER","children":[
                 {"type":"SERVICE","leistungId":1,"quantity":3},
                 {"type":"SERVICE","leistungId":2,"quantity":4,"optional":true},
                 {"type":"SERVICE","leistungId":3},
                 {"type":"SERVICE","leistungId":3,"quantity":null}]},
             {"type":"SECTION_HEADER","sectionLabel":"ohne Kinder"}]
            """;

    @Test
    void findetLeistungenAufAllenEbenenAuchOptionale() {
        assertThat(LeistungsPositionen.leistungIds(POSITIONEN)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void summiertMengenOhneOptionaleUndMerktSichDieKategorie() {
        Map<Long, BigDecimal> mengen = new HashMap<>();
        Map<Long, Long> kategorien = new HashMap<>();

        LeistungsPositionen.sammleMengen(POSITIONEN, mengen, kategorien);

        assertThat(mengen).containsOnlyKeys(1L, 3L);
        assertThat(mengen.get(1L)).isEqualByComparingTo("5");
        assertThat(mengen.get(3L)).isEqualByComparingTo("0");
        assertThat(kategorien).containsExactly(Map.entry(1L, 10L));
    }

    @Test
    void liestAuchDasFormatMitBlocks() {
        String json = "{\"blocks\":[{\"type\":\"SERVICE\",\"leistungId\":7,\"quantity\":1,\"kategorieId\":null}]}";
        Map<Long, BigDecimal> mengen = new HashMap<>();
        Map<Long, Long> kategorien = new HashMap<>();

        LeistungsPositionen.sammleMengen(json, mengen, kategorien);

        assertThat(LeistungsPositionen.leistungIds(json)).containsExactly(7L);
        assertThat(mengen.get(7L)).isEqualByComparingTo("1");
        assertThat(kategorien).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "kein json", "{\"blocks\":\"falsch\"}", "{\"andere\":1}", "42" })
    void unlesbareOderLeerePositionenErgebenNichts(String json) {
        Map<Long, BigDecimal> mengen = new HashMap<>();
        LeistungsPositionen.sammleMengen(json, mengen, new HashMap<>());

        assertThat(LeistungsPositionen.leistungIds(json)).isEmpty();
        assertThat(mengen).isEmpty();
    }
}
