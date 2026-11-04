package org.example.kalkulationsprogramm.util.suche;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailSuchNormalisierungTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Würth, wurth",
            "WÜRTH, wurth",
            "Müller Geländer, muller gelander",
            "Straße, strasse",
            "STRASSE, strasse",
            "Öffnung, offnung",
            "Café Crème, cafe creme",
            "Søren, søren",
            "Mueller, muller",
            "MÜLLER, muller",
            "Wuerth, wurth",
            "Steuerberater, steurberater",
            "Moeller Baer, moller bar",
    })
    void normalisiert(String eingabe, String erwartet) {
        assertThat(EmailSuchNormalisierung.normalisiere(eingabe)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Großes ẞ wird wie ß zu ss")
    void grossesEszett() {
        assertThat(EmailSuchNormalisierung.normalisiere("GROẞ")).isEqualTo("gross");
    }

    @Test
    @DisplayName("Leerraum (auch Zeilenumbruch, Tab, geschütztes Leerzeichen) wird zu einem Leerzeichen")
    void leerraum() {
        assertThat(EmailSuchNormalisierung.normalisiere("  Max \t\r\n Mustermann GmbH  "))
                .isEqualTo("max mustermann gmbh");
    }

    @Test
    void nullUndLeer() {
        assertThat(EmailSuchNormalisierung.normalisiere(null)).isEmpty();
        assertThat(EmailSuchNormalisierung.normalisiere("   ")).isEmpty();
    }

    @Test
    @DisplayName("Umschreibung in beide Richtungen: Ü und ue ergeben dasselbe")
    void umschreibungBeidseitig() {
        assertThat(EmailSuchNormalisierung.normalisiere("Würth"))
                .isEqualTo(EmailSuchNormalisierung.normalisiere("Wuerth"))
                .isEqualTo(EmailSuchNormalisierung.normalisiere("wurth"));
        assertThat(EmailSuchNormalisierung.normalisiere("Steuerberater"))
                .contains(EmailSuchNormalisierung.normalisiere("steuer"));
    }

    @Test
    @DisplayName("Suchwörter: an Leerraum geteilt und normalisiert wie der Suchtext")
    void suchwoerter() {
        assertThat(EmailSuchNormalisierung.suchwoerter("  Wuerth   GELÄNDER ")).containsExactly("wurth", "gelander");
    }

    @Test
    @DisplayName("Höchstens 10 Wörter, der Rest fällt weg; doppelte Wörter zählen einmal")
    void hoechstensZehnWoerter() {
        List<String> woerter = EmailSuchNormalisierung.suchwoerter("a1 a2 a3 a1 a4 a5 a6 a7 a8 a9 a10 a11 a12");

        assertThat(woerter).hasSize(10).doesNotHaveDuplicates();
        assertThat(woerter.get(9)).isEqualTo("a10");
    }

    @Test
    @DisplayName("Nur Satzzeichen-freie Leerstellen: leere Eingabe ergibt keine Wörter")
    void keineWoerter() {
        assertThat(EmailSuchNormalisierung.suchwoerter(null)).isEmpty();
        assertThat(EmailSuchNormalisierung.suchwoerter("   ")).isEmpty();
    }

    @Test
    @DisplayName("Angriffstexte bleiben reiner Text (keine Sonderbehandlung, Escaping macht die Abfrage)")
    void angriffstexte() {
        assertThat(EmailSuchNormalisierung.suchwoerter("'; DROP TABLE email; --"))
                .containsExactly("';", "drop", "table", "email;", "--");
    }
}
