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

    @Test
    @DisplayName("Stammnummer aus Durchwahl-Schreibweise: Zentrale -0 und Durchwahlen bis 5 Ziffern")
    void stammnummer() {
        assertThat(RufnummerNormalisierer.stammnummer("09721 5555-0", "49", "0931")).isEqualTo("+4997215555");
        assertThat(RufnummerNormalisierer.stammnummer("0931 4444 - 12", "49", "0931")).isEqualTo("+499314444");
        assertThat(RufnummerNormalisierer.stammnummer("+49 (0) 9721 5555-12345", "49", "0931")).isEqualTo("+4997215555");
        assertThat(RufnummerNormalisierer.stammnummer("4444-12", "49", "0931")).isEqualTo("+499314444");
    }

    @Test
    @DisplayName("Keine Stammnummer: ohne Bindestrich, zu lange Durchwahl, Buchstaben oder zu kurzer Stamm")
    void keineStammnummer() {
        assertThat(RufnummerNormalisierer.stammnummer(null, "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931 12345", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("-12", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931 12345-", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931-1234567", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931 12345-1a", "49", "0931")).isNull();
        // Bindestrich trennt nur die Vorwahl ab: Stamm "+499721" ist zu kurz.
        assertThat(RufnummerNormalisierer.stammnummer("09721-5555", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931 12-34", "49", "0931")).isNull();
        // Nur gegliedert, nicht Stamm + Durchwahl: mehrere Bindestriche.
        assertThat(RufnummerNormalisierer.stammnummer("09721 12-34-56", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("0931-4444-12", "49", "0931")).isNull();
        // Handys haben keine Durchwahlen.
        assertThat(RufnummerNormalisierer.stammnummer("0171 1234-567", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("+49 160 12345-6", "49", "0931")).isNull();
        assertThat(RufnummerNormalisierer.stammnummer("01521 12345-67", "49", "0931")).isNull();
    }
}
