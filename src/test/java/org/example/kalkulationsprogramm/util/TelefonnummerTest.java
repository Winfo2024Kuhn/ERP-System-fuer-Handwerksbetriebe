package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TelefonnummerTest {

    @Nested
    @DisplayName("vergleichsziffern")
    class Vergleichsziffern {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
                "+49 30 1234567, 0301234567",
                "0049 30 1234567, 0301234567",
                "030/123-4567, 0301234567",
                "030 123 4567, 0301234567",
                "'  0931 12345  ', 093112345",
                "+43 1 234567, 431234567"
        })
        @DisplayName("Telefonnummer wird auf Ziffern reduziert; +49 / 0049 → 0")
        void reduziertAufZiffern(String roh, String erwartet) {
            assertThat(Telefonnummer.vergleichsziffern(roh)).isEqualTo(erwartet);
        }

        @ParameterizedTest(name = "\"{0}\" → null")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "0931 ", "0931", "030", "02", "+49 931", "123456"})
        @DisplayName("Leer oder unter 7 Ziffern liefert null – eine Vorwahl würde sonst alle Kunden eines Orts treffen")
        void kurzeNummernZaehlenNicht(String roh) {
            assertThat(Telefonnummer.vergleichsziffern(roh)).isNull();
        }
    }

    @Nested
    @DisplayName("ohneReineVorwahl")
    class OhneReineVorwahl {

        @ParameterizedTest(name = "\"{0}\" → null")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "0931 ", "0931", " 089 ", "+49 931", "0049 931", "+49", "0931 12"})
        @DisplayName("Leere Eingabe und reine Vorwahl werden nicht gespeichert")
        void reineVorwahlWirdNull(String roh) {
            assertThat(Telefonnummer.ohneReineVorwahl(roh)).isNull();
        }

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({
                "'0931 123', 0931 123",
                "'  0931 123456 ', 0931 123456",
                "+49 170 1234567, +49 170 1234567",
                "0049 931 1234, 0049 931 1234",
                "030/123-4567, 030/123-4567"
        })
        @DisplayName("Vollständige Nummern bleiben, wie sie eingegeben wurden (nur getrimmt)")
        void vollstaendigeNummerBleibt(String roh, String erwartet) {
            assertThat(Telefonnummer.ohneReineVorwahl(roh)).isEqualTo(erwartet);
        }

        @Test
        @DisplayName("Kurze Ortsnummer ohne Vorwahl bleibt – die Anrufer-Erkennung ergänzt die Ortsvorwahl")
        void ortsnummerOhneVorwahlBleibt() {
            assertThat(Telefonnummer.ohneReineVorwahl("12345")).isEqualTo("12345");
            assertThat(Telefonnummer.ohneReineVorwahl("12 34 56")).isEqualTo("12 34 56");
        }
    }
}
