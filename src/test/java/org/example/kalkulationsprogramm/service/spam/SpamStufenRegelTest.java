package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.SpamStufe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Grenzwerte der drei Stufen. Nur SPAM verschiebt eine Mail (auch auf dem Mail-Server).
 */
class SpamStufenRegelTest {

    private static SpamStufe stufe(Double b, int r) {
        return SpamStufenRegel.bestimme(b, r, false);
    }

    @Nested
    class LernfilterMitRegeln {

        @ParameterizedTest(name = "B={0}, R={1} → {2}")
        @CsvSource({
                "0.949, 25, UNSICHER",
                "0.95, 25, SPAM",
                "0.95, 24, UNSICHER",
                "0.97, 85, SPAM"
        })
        void sichererLernfilterBrauchtMindestens25Regelpunkte(double b, int r, SpamStufe erwartet) {
            assertThat(SpamStufenRegel.bestimme(b, r, false)).isEqualTo(erwartet);
        }
    }

    @Nested
    class LernfilterAllein {

        @ParameterizedTest(name = "B={0} → {1}")
        @CsvSource({"0.999, UNSICHER", "1.0, UNSICHER", "0.95, UNSICHER", "0.60, UNSICHER", "0.59, KEIN_SPAM"})
        void lernfilterAlleinErgibtNieSpam(double b, SpamStufe erwartet) {
            // Probelauf gegen eine Prod-Kopie: Der Lernfilter gab echten Mails ≥ 0,99.
            assertThat(SpamStufenRegel.bestimme(b, 0, false)).isEqualTo(erwartet);
        }
    }

    @Nested
    class RegelnSicher {

        @ParameterizedTest(name = "B={0}, R={1} → {2}")
        @CsvSource({
                ", 85, SPAM",
                ", 84, UNSICHER",
                "0.21, 85, SPAM",
                "0.20, 85, UNSICHER",
                "0.05, 100, UNSICHER"
        })
        void regelnAb85SindSpamWennLernfilterNichtWiderspricht(Double b, int r, SpamStufe erwartet) {
            assertThat(stufe(b, r)).isEqualTo(erwartet);
        }
    }

    @Nested
    class KeinSpamUndUnsicher {

        @ParameterizedTest(name = "B={0}, R={1} → {2}")
        @CsvSource({
                "0.20, 39, KEIN_SPAM",
                "0.20, 40, UNSICHER",
                "0.21, 39, KEIN_SPAM",
                "0.59, 0, KEIN_SPAM",
                "0.60, 0, UNSICHER",
                "0.0, 0, KEIN_SPAM"
        })
        void grenzen(Double b, int r, SpamStufe erwartet) {
            assertThat(stufe(b, r)).isEqualTo(erwartet);
        }

        @ParameterizedTest(name = "ohne Modell, R={0} → {1}")
        @CsvSource({
                "0, KEIN_SPAM",
                "39, KEIN_SPAM",
                "40, UNSICHER"
        })
        void modellNichtBereit(int r, SpamStufe erwartet) {
            assertThat(stufe(null, r)).isEqualTo(erwartet);
        }

        @Test
        void gefaehrlicherAnhangAlleinIstUnsicher() {
            assertThat(SpamStufenRegel.bestimme(null, 0, true)).isEqualTo(SpamStufe.UNSICHER);
            assertThat(SpamStufenRegel.bestimme(0.4, 0, true)).isEqualTo(SpamStufe.UNSICHER);
        }

        @Test
        void gefaehrlicherAnhangBleibtUnsicherTrotzHarmlosemLernfilter() {
            // Inhaber-Entscheidung: Der Hinweis erscheint immer, verschoben wird nichts.
            assertThat(SpamStufenRegel.bestimme(0.1, 0, true)).isEqualTo(SpamStufe.UNSICHER);
            assertThat(SpamStufenRegel.bestimme(0.0, 39, true)).isEqualTo(SpamStufe.UNSICHER);
        }

        @Test
        void ungueltigerLernfilterWertZaehltWieFehlend() {
            assertThat(stufe(Double.NaN, 85)).isEqualTo(SpamStufe.SPAM);
            assertThat(stufe(Double.NaN, 10)).isEqualTo(SpamStufe.KEIN_SPAM);
        }
    }

    @Nested
    class Gruppenpunkte {

        @Test
        void einSignalAlleinGibtNichts() {
            assertThat(SpamStufenRegel.gruppenPunkte(null)).isZero();
            assertThat(SpamStufenRegel.gruppenPunkte(Set.of())).isZero();
            assertThat(SpamStufenRegel.gruppenPunkte(EnumSet.of(SpamSignal.ANTWORT_UMGELEITET))).isZero();
            assertThat(SpamStufenRegel.gruppenPunkte(EnumSet.of(SpamSignal.GEFAEHRLICHER_ANHANG))).isZero();
        }

        @Test
        void zweiUndDreiSignale() {
            assertThat(SpamStufenRegel.gruppenPunkte(
                    EnumSet.of(SpamSignal.ANTWORT_UMGELEITET, SpamSignal.DATUM_FEHLT))).isEqualTo(40);
            assertThat(SpamStufenRegel.gruppenPunkte(EnumSet.of(SpamSignal.ANTWORT_UMGELEITET,
                    SpamSignal.DATUM_FEHLT, SpamSignal.ANBIETER_SPAM))).isEqualTo(70);
            assertThat(SpamStufenRegel.gruppenPunkte(EnumSet.allOf(SpamSignal.class))).isEqualTo(85);
        }

        @Test
        void gefaehrlicherAnhangMitEinemWeiterenSignal() {
            assertThat(SpamStufenRegel.gruppenPunkte(
                    EnumSet.of(SpamSignal.GEFAEHRLICHER_ANHANG, SpamSignal.DATUM_FEHLT))).isEqualTo(85);
        }
    }

    @Nested
    class Anzeigewert {

        @Test
        void mischtRegelnUndLernfilter() {
            assertThat(SpamStufenRegel.anzeigeWert(40, 0.5)).isEqualTo(46);
            assertThat(SpamStufenRegel.anzeigeWert(0, 0.94)).isEqualTo(56);
            assertThat(SpamStufenRegel.anzeigeWert(100, 1.0)).isEqualTo(100);
        }

        @Test
        void ohneLernfilterNurRegeln() {
            assertThat(SpamStufenRegel.anzeigeWert(40, null)).isEqualTo(40);
            assertThat(SpamStufenRegel.anzeigeWert(40, Double.NaN)).isEqualTo(40);
        }

        @Test
        void bleibtZwischen0Und100() {
            assertThat(SpamStufenRegel.anzeigeWert(-10, null)).isZero();
            assertThat(SpamStufenRegel.anzeigeWert(250, null)).isEqualTo(100);
        }
    }
}
