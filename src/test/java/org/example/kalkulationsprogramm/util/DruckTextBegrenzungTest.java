package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Client-Texte, die einzeilig ins PDF gedruckt werden: bereinigt und begrenzt. */
class DruckTextBegrenzungTest {

    @Test
    void nullBleibtNull() {
        assertThat(DruckTextBegrenzung.begrenze(null, 10)).isNull();
    }

    @Test
    void leerOderNurLeerzeichenWirdNull() {
        assertThat(DruckTextBegrenzung.begrenze("", 10)).isNull();
        assertThat(DruckTextBegrenzung.begrenze("   \t\n ", 10)).isNull();
    }

    @Test
    void trimmtAussenUndLaesstInnenStehen() {
        assertThat(DruckTextBegrenzung.begrenze("  Max  Mustermann  ", 80)).isEqualTo("Max  Mustermann");
    }

    @Test
    void ersetztSteuerzeichenUndZeilenumbruecheDurchLeerzeichen() {
        assertThat(DruckTextBegrenzung.begrenze("Zeile 1\nZeile 2\r\tEnde\u0000x\u0085y z", 80))
                .isEqualTo("Zeile 1 Zeile 2  Ende x y z");
    }

    @Test
    void ersetztUnsichtbareFormatzeichenDurchLeerzeichen() {
        // U+202E dreht die Leserichtung um, U+200B ist ein unsichtbares Leerzeichen, U+FEFF ein BOM.
        assertThat(DruckTextBegrenzung.begrenze("Max\u202EMustermann\u200Bx\uFEFFy", 80))
                .isEqualTo("Max Mustermann x y");
        assertThat(DruckTextBegrenzung.begrenze("\u200B\u202E", 80)).isNull();
    }

    @Test
    void kurzerTextBleibtUnveraendert() {
        assertThat(DruckTextBegrenzung.begrenze("Gerüststellung", 14)).isEqualTo("Gerüststellung");
    }

    @Test
    void ueberlangerTextWirdMitAuslassungszeichenAufDieGrenzeGekuerzt() {
        String ergebnis = DruckTextBegrenzung.begrenze("x".repeat(500), 200);

        assertThat(ergebnis).hasSize(200).endsWith("…").startsWith("xxx");
    }

    @Test
    void kuerztNichtMittenInEinZeichenAusZweiTeilen() {
        // Emoji = zwei char (Surrogatpaar); die Grenze zaehlt Zeichen, nicht char.
        String ergebnis = DruckTextBegrenzung.begrenze("🔨".repeat(5), 3);

        assertThat(ergebnis).isEqualTo("🔨🔨…");
    }

    @Test
    void htmlBleibtWoertlicherText() {
        assertThat(DruckTextBegrenzung.begrenze("<script>alert(1)</script>", 80))
                .isEqualTo("<script>alert(1)</script>");
    }
}
