package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tolerantes Lesen von Geldbeträgen aus KI-Antworten. Die fachlichen Schreibweisen
 * (Tausender, Dezimalzeichen) prüft zusätzlich {@code SteuerberaterBwaGemeinkostenTest}.
 */
class KiGeldbetragTest {

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    private BigDecimal lese(String json) {
        JsonNode root = mapper.readTree(json);
        return KiGeldbetrag.lese(root, "betrag");
    }

    @Test
    void ohneWurzelOderFeldGibtEsKeinenBetrag() {
        assertThat(KiGeldbetrag.lese(null, "betrag")).isNull();
        assertThat(lese("{}")).isNull();
        assertThat(lese("{\"betrag\": null}")).isNull();
    }

    @Test
    void zahlenWerdenAlsAbsolutwertGelesen() {
        assertThat(lese("{\"betrag\": -1234.56}").toPlainString()).isEqualTo("1234.56");
    }

    @Test
    void wederZahlNochTextGibtKeinenBetrag() {
        assertThat(lese("{\"betrag\": true}")).isNull();
        assertThat(lese("{\"betrag\": {\"wert\": 1}}")).isNull();
    }

    @Test
    void textOhneZiffernOderUnlesbarGibtKeinenBetrag() {
        assertThat(lese("{\"betrag\": \"EUR\"}")).isNull();
        assertThat(lese("{\"betrag\": \"1-2\"}")).isNull();
    }

    @Test
    void liestDeutscheUndEnglischeSchreibweise() {
        assertThat(lese("{\"betrag\": \"1.234,56 EUR\"}")).isEqualByComparingTo("1234.56");
        assertThat(lese("{\"betrag\": \"1,234.56\"}")).isEqualByComparingTo("1234.56");
        assertThat(lese("{\"betrag\": \"12.500\"}")).isEqualByComparingTo("12500");
        assertThat(lese("{\"betrag\": \"12.5\"}")).isEqualByComparingTo("12.5");
        assertThat(lese("{\"betrag\": \"12,500\"}")).isEqualByComparingTo("12500");
        assertThat(lese("{\"betrag\": \"0,50\"}")).isEqualByComparingTo("0.50");
        assertThat(lese("{\"betrag\": \"15000\"}")).isEqualByComparingTo("15000");
    }
}
