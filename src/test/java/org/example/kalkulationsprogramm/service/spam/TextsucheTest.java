package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextsucheTest {

    @Test
    void normalisiertUmlauteGrossschreibungUndLeerraum() {
        assertThat(Textsuche.normalisiere("  Änderung der\n\tBANKVERBINDUNG – Straße Öl Über  "))
                .isEqualTo("aenderung der bankverbindung – strasse oel ueber");
        assertThat(Textsuche.normalisiere(null)).isEmpty();
        assertThat(Textsuche.normalisiere("")).isEmpty();
        assertThat(Textsuche.normalisiere("abcdef", 3)).isEqualTo("abc");
    }

    @Test
    void ganzesWortUndWortanfang() {
        String text = "die an sie gerichtete anfrage, das gericht, zahlungsfrist";
        assertThat(Textsuche.enthaeltWort(text, "gericht")).isTrue();
        assertThat(Textsuche.enthaeltWort("an sie gerichtet", "gericht")).isFalse();
        assertThat(Textsuche.enthaeltWortanfang(text, "zahlung")).isTrue();
        assertThat(Textsuche.enthaeltWortanfang("nachzahlung", "zahlung")).isFalse();
        assertThat(Textsuche.enthaeltWort(text, "")).isFalse();
        assertThat(Textsuche.enthaeltWort(null, "x")).isFalse();
        assertThat(Textsuche.enthaeltIrgendeinWort(text, List.of("foo", "gericht"))).isTrue();
        assertThat(Textsuche.enthaeltIrgendeinWort(text, List.of("foo"))).isFalse();
        assertThat(Textsuche.enthaeltIrgendeinenWortanfang(text, List.of("foo"))).isFalse();
    }

    @Test
    void naheBeieinander() {
        String text = "unsere bankverbindung hat sich geaendert";
        assertThat(Textsuche.naheBeieinander(text, List.of("bankverbindung"), List.of("geaendert"), 25)).isTrue();
        assertThat(Textsuche.naheBeieinander(text, List.of("bankverbindung"), List.of("geaendert"), 5)).isFalse();
        assertThat(Textsuche.naheBeieinander("neue bankverbindung", List.of("bankverbindung"), List.of("neue "), 10))
                .isTrue();
        assertThat(Textsuche.naheBeieinander(text, List.of("iban"), List.of("geaendert"), 25)).isFalse();
    }
}
