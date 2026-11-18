package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class GedankenstrichBereinigungTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "Anbei die Preise – wie besprochen.|Anbei die Preise, wie besprochen.",
            "Anbei die Preise — wie besprochen.|Anbei die Preise, wie besprochen.",
            "Anbei die Preise -- wie besprochen.|Anbei die Preise, wie besprochen.",
            "Anbei die Preise - wie besprochen.|Anbei die Preise, wie besprochen.",
            "Der Termin – Montag früh – passt.|Der Termin, Montag früh, passt.",
            "Das Geländer—aus Edelstahl—kommt Freitag.|Das Geländer, aus Edelstahl, kommt Freitag.",
            "Das Geländer — aus Edelstahl — kommt Freitag.|Das Geländer, aus Edelstahl, kommt Freitag.",
            "Danke für die Info - ich melde mich.|Danke für die Info, ich melde mich.",
            "Wort--Wort|Wort, Wort",
    })
    @DisplayName("Gedankenstriche als Satzzeichen werden zum Komma")
    void gedankenstrichWirdKomma(String eingabe, String erwartet) {
        assertThat(GedankenstrichBereinigung.bereinige(eingabe)).isEqualTo(erwartet);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "Hallo Herr Mustermann, – anbei die Preise.|Hallo Herr Mustermann, anbei die Preise.",
            "Alles klar – .|Alles klar.",
            "Alles klar –|Alles klar",
            "Alles klar —!|Alles klar!",
            "Bis Montag (– oder Dienstag).|Bis Montag (oder Dienstag).",
            "Bis Montag ( – oder Dienstag).|Bis Montag (oder Dienstag).",
    })
    @DisplayName("Neben anderen Satzzeichen fällt der Strich ersatzlos weg")
    void nebenSatzzeichenFaelltWeg(String eingabe, String erwartet) {
        assertThat(GedankenstrichBereinigung.bereinige(eingabe)).isEqualTo(erwartet);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "Bitte per E-Mail an den Metallbau-Meister.",
            "Wir kommen von 8–10 Uhr.",
            "Geöffnet Mo–Fr.",
            "Bei -5 °C streichen wir nicht.",
            "Metallbau- und Schlosserarbeiten",
            "Zeitraum 8 - 10 Uhr",
            "Seite 2020–2024",
            "- Geländer\n- Tor",
            "– erster Punkt",
            " – Punkt nach Leerzeichen am Zeilenanfang",
            "Preis –5 €",
            "https://example.com/a-b-c",
            "",
    })
    @DisplayName("Bindestriche, Bereiche, Minus und Aufzählungsstriche bleiben unverändert")
    void bleibtUnveraendert(String text) {
        assertThat(GedankenstrichBereinigung.bereinige(text)).isEqualTo(text);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "Geöffnet Mo - Fr von 7 bis 16 Uhr.|Geöffnet Mo–Fr von 7 bis 16 Uhr.",
            "Montag - Freitag|Montag–Freitag",
            "Sa -- So geschlossen|Sa–So geschlossen",
            "Di – Do kommen wir.|Di–Do kommen wir.",
            "Mo. - Fr.|Mo.–Fr.",
            "Mo—Fr geöffnet|Mo–Fr geöffnet",
            "Am Montag - wie besprochen - kommen wir.|Am Montag, wie besprochen, kommen wir.",
    })
    @DisplayName("Bereiche zwischen Wochentagen werden zum Bis-Strich ohne Leerzeichen")
    void wochentage(String eingabe, String erwartet) {
        assertThat(GedankenstrichBereinigung.bereinige(eingabe)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Geschützte Leerzeichen zählen wie normale Leerzeichen")
    void geschuetzteLeerzeichen() {
        assertThat(GedankenstrichBereinigung.bereinige("Preise – wie besprochen"))
                .isEqualTo("Preise, wie besprochen");
    }

    @Test
    @DisplayName("Mehrere Striche in einer Zeile und über Zeilen hinweg")
    void mehrereStriche() {
        assertThat(GedankenstrichBereinigung.bereinige("A – B – C\nD - E"))
                .isEqualTo("A, B, C\nD, E");
    }

    @Test
    @DisplayName("null bleibt null")
    void nullBleibtNull() {
        assertThat(GedankenstrichBereinigung.bereinige(null)).isNull();
    }

    @Test
    @DisplayName("Lange Eingaben mit vielen Strichen und Leerzeichen laufen in linearer Zeit (ReDoS)")
    void keinReDoS() {
        String boese = "a" + " ".repeat(50_000) + "–" + " ".repeat(50_000) + "-".repeat(50_000) + "!";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> GedankenstrichBereinigung.bereinige(boese));
        String vieleStriche = "Wort – ".repeat(20_000);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> GedankenstrichBereinigung.bereinige(vieleStriche));
    }
}
