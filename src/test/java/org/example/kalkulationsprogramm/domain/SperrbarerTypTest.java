package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SperrbarerTypTest {

    @Test
    void enthaeltGenauDieStartwerteAusgangUndEingang() {
        assertThat(SperrbarerTyp.values()).containsExactly(SperrbarerTyp.AUSGANG, SperrbarerTyp.EINGANG);
    }

    @Test
    void ausTextErkenntKleinschreibungUndTrimmtWhitespace() {
        assertThat(SperrbarerTyp.ausText(" ausgang ")).contains(SperrbarerTyp.AUSGANG);
    }

    @Test
    void ausTextErkenntGrossschreibungOhneWhitespace() {
        assertThat(SperrbarerTyp.ausText("EINGANG")).contains(SperrbarerTyp.EINGANG);
    }

    @Test
    void ausTextGibtLeerBeiNullZurueck() {
        assertThat(SperrbarerTyp.ausText(null)).isEmpty();
    }

    @Test
    void ausTextGibtLeerBeiLeerstringZurueck() {
        assertThat(SperrbarerTyp.ausText("")).isEmpty();
    }

    @Test
    void ausTextGibtLeerBeiUnbekanntemWertZurueck() {
        assertThat(SperrbarerTyp.ausText("PROJEKT")).isEmpty();
    }

    @Test
    void ausTextUeberstehtSqlInjectionVersuchOhneAusnahme() {
        // Sicherheits-Checkliste TESTING_SECURITY.md: SQL-Injection-String darf
        // keine Ausnahme ausloesen, sondern muss als unbekannter Wert behandelt werden.
        assertThat(SperrbarerTyp.ausText("'; DROP TABLE datensatz_lock; --")).isEmpty();
    }

    @Test
    void ausTextUeberstehtXssVersuchOhneAusnahme() {
        assertThat(SperrbarerTyp.ausText("<script>alert(1)</script>")).isEmpty();
    }

    @Test
    void ausTextGibtLeerBeiUeberlangemTextZurueckOhneAusnahme() {
        String ueberlang = "A".repeat(10_001);
        assertThat(SperrbarerTyp.ausText(ueberlang)).isEmpty();
    }
}
