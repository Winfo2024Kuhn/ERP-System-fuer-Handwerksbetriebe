package org.example.kalkulationsprogramm.service.telefon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RufnummerNormalisiererTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("Übliche Schreibweisen ergeben dieselbe E.164-Nummer")
    @CsvSource(delimiter = '|', value = {
            "0931 1234567|+499311234567",
            "0931/123 45-67|+499311234567",
            "(0931) 12345-67|+499311234567",
            "+49 931 1234567|+499311234567",
            "+49 (0) 931 1234567|+499311234567",
            "+49(0)9311234567|+499311234567",
            "0049 931 1234567|+499311234567",
            "0049-931-1234567|+499311234567",
            "1234567|+499311234567",
            "0171 1234567|+491711234567",
            "+43 1 1234567|+4311234567",
            "0043 1 1234567|+4311234567",
            "  0931.123.4567  |+499311234567"
    })
    void normalisiertSchreibweisen(String roh, String erwartet) {
        assertThat(RufnummerNormalisierer.normalisiere(roh, "49", "0931")).isEqualTo(erwartet);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"anonym", "unbekannt", "  ", "+", "12", "---", "<script>alert(1)</script>"})
    @DisplayName("Ohne erkennbare Nummer → null")
    void ohneNummerNull(String roh) {
        assertThat(RufnummerNormalisierer.normalisiere(roh, "49", "0931")).isNull();
    }

    @Test
    @DisplayName("Landesvorwahl in jeder Schreibweise, leer bedeutet Deutschland")
    void landesvorwahlSchreibweisen() {
        assertThat(RufnummerNormalisierer.normalisiere("0931 1234567", "+49", "0931")).isEqualTo("+499311234567");
        assertThat(RufnummerNormalisierer.normalisiere("0931 1234567", "0049", "0931")).isEqualTo("+499311234567");
        assertThat(RufnummerNormalisierer.normalisiere("0931 1234567", null, null)).isEqualTo("+499311234567");
        assertThat(RufnummerNormalisierer.normalisiere("01 1234567", "43", "")).isEqualTo("+4311234567");
    }

    @Test
    @DisplayName("Ortsnetznummer ohne bekannte Ortsvorwahl wird nicht geraten")
    void ortsnetzOhneOrtsvorwahl() {
        assertThat(RufnummerNormalisierer.normalisiere("1234567", "49", null)).isNull();
        assertThat(RufnummerNormalisierer.normalisiere("1234567", "49", "")).isNull();
    }

    @Test
    @DisplayName("Ortsvorwahl mit oder ohne führende Null")
    void ortsvorwahlOhneNull() {
        assertThat(RufnummerNormalisierer.normalisiere("1234567", "49", "931")).isEqualTo("+499311234567");
    }

    @Test
    @DisplayName("Plus mitten in der Nummer wird ignoriert, Klammern ohne 0 bleiben Ziffern")
    void sonderfaelle() {
        assertThat(RufnummerNormalisierer.normalisiere("0931+1234567", "49", "0931")).isEqualTo("+499311234567");
        assertThat(RufnummerNormalisierer.normalisiere("(09) 311234567", "49", "0931")).isEqualTo("+499311234567");
        assertThat(RufnummerNormalisierer.normalisiere("(0", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.normalisiere("+49 ( 0 ) 931 1234567", "49", "0931")).isEqualTo("+499311234567");
    }

    @Test
    @DisplayName("Überlange Eingaben werden verworfen statt gespeichert")
    void ueberlang() {
        assertThat(RufnummerNormalisierer.normalisiere("0".repeat(10_001) + "1", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.normalisiere("1".repeat(50), "49", "0931")).isNull();
    }
}
