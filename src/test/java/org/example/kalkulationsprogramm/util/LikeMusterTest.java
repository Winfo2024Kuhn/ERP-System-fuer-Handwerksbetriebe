package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikeMusterTest {

    @Test
    @DisplayName("Platzhalter und Escape-Zeichen werden maskiert, normaler Text bleibt")
    void escape() {
        assertThat(LikeMuster.escape("Torprüfung")).isEqualTo("Torprüfung");
        assertThat(LikeMuster.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
        assertThat(LikeMuster.escape("")).isEmpty();
    }

    @Test
    @DisplayName("„enthält“-Muster ist klein geschrieben und maskiert")
    void enthaelt() {
        assertThat(LikeMuster.enthaelt("Tor_PRÜFUNG")).isEqualTo("%tor\\_prüfung%");
        assertThat(LikeMuster.enthaelt("'; DROP TABLE x; --")).isEqualTo("%'; drop table x; --%");
    }
}
