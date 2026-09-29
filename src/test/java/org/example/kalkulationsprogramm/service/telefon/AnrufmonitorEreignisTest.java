package org.example.kalkulationsprogramm.service.telefon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnrufmonitorEreignisTest {

    @Test
    @DisplayName("RING: Anrufer und angerufene eigene Nummer")
    void ring() {
        AnrufmonitorEreignis e = AnrufmonitorEreignis.parse("29.09.26 11:55:01;RING;0;09311234567;2323;SIP0;");
        assertThat(e).isEqualTo(new AnrufmonitorEreignis(AnrufmonitorEreignis.Typ.RING, "0", "09311234567", "2323", null, null));
    }

    @Test
    @DisplayName("CALL: Nebenstelle, genutzte eigene Nummer, Ziel")
    void call() {
        AnrufmonitorEreignis e = AnrufmonitorEreignis.parse("29.09.26 11:55:01;CALL;1;10;2323;09317654321;SIP0;");
        assertThat(e).isEqualTo(new AnrufmonitorEreignis(AnrufmonitorEreignis.Typ.CALL, "1", "09317654321", "2323", 10, null));
        assertThat(e.istAnrufbeantworter()).isFalse();
    }

    @Test
    @DisplayName("CONNECT durch den Anrufbeantworter (Nebenstelle 40–49)")
    void connectAnrufbeantworter() {
        AnrufmonitorEreignis e = AnrufmonitorEreignis.parse("29.09.26 11:55:31;CONNECT;0;41;09311234567;");
        assertThat(e.typ()).isEqualTo(AnrufmonitorEreignis.Typ.CONNECT);
        assertThat(e.nebenstelle()).isEqualTo(41);
        assertThat(e.istAnrufbeantworter()).isTrue();
    }

    @Test
    @DisplayName("DISCONNECT mit Dauer")
    void disconnect() {
        AnrufmonitorEreignis e = AnrufmonitorEreignis.parse("29.09.26 11:56:31;DISCONNECT;0;60;");
        assertThat(e.typ()).isEqualTo(AnrufmonitorEreignis.Typ.DISCONNECT);
        assertThat(e.dauerSekunden()).isEqualTo(60);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Hallo", "a;b;c", "29.09.26;FOO;0;1;", "29.09.26;RING;;0931;2323;", "29.09.26;CONNECT;0;x;0931;"})
    @DisplayName("Unbekannte oder kaputte Zeilen werden ignoriert bzw. robust gelesen")
    void kaputt(String zeile) {
        AnrufmonitorEreignis e = AnrufmonitorEreignis.parse(zeile);
        if (e != null) {
            assertThat(e.nebenstelle()).isNull();
        }
    }

    @Test
    void zuLang() {
        assertThat(AnrufmonitorEreignis.parse("x".repeat(501))).isNull();
    }
}
