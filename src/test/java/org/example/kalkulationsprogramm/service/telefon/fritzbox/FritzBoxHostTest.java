package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FritzBoxHostTest {

    @ParameterizedTest
    @ValueSource(strings = {"fritz.box", "192.168.178.1", "FRITZ.BOX", "meine-box.local"})
    void gueltig(String host) {
        assertThat(FritzBoxHost.istGueltig(host)).isTrue();
        assertThat(FritzBoxHost.pruefe("  " + host + " ")).isEqualTo(host);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"http://fritz.box", "fritz.box:49000", "fritz.box/pfad", "user@fritz.box", "..", "a..b",
            ".fritz.box", "-box", "box-", "fritz box", "evil.com#@fritz.box", "[::1]", "'; DROP TABLE x; --"})
    void ungueltig(String host) {
        assertThat(FritzBoxHost.istGueltig(host)).isFalse();
        assertThatThrownBy(() -> FritzBoxHost.pruefe(host)).isInstanceOf(TelefonAnlageException.class);
    }

    @org.junit.jupiter.api.Test
    void zuLang() {
        assertThat(FritzBoxHost.istGueltig("a".repeat(254))).isFalse();
    }
}
