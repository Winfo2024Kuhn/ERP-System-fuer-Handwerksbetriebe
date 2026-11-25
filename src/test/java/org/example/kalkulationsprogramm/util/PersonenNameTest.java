package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonenNameTest {

    @Test
    @DisplayName("„Vorname Nachname“ ohne überzählige Leerzeichen, leer wenn beides fehlt")
    void vollerName() {
        assertThat(PersonenName.vollerName("Max", "Mustermann")).isEqualTo("Max Mustermann");
        assertThat(PersonenName.vollerName("  Max ", " Mustermann  ")).isEqualTo("Max Mustermann");
        assertThat(PersonenName.vollerName(null, "Mustermann")).isEqualTo("Mustermann");
        assertThat(PersonenName.vollerName(" ", "Mustermann")).isEqualTo("Mustermann");
        assertThat(PersonenName.vollerName("Erika", null)).isEqualTo("Erika");
        assertThat(PersonenName.vollerName(null, null)).isEmpty();
    }
}
