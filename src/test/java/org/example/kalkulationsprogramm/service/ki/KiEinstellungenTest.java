package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ablage der KI-Einstellungen: Schlüssel verschlüsselt, Eingaben geprüft. Nur Test-Schlüssel. */
class KiEinstellungenTest {

    private final EinstellungenImSpeicher speicher = new EinstellungenImSpeicher();
    private final KiEinstellungen einstellungen = speicher.einstellungen;
    private final TestAnbieter mistral = TestAnbieter.mistral();
    private final TestAnbieter gemini = TestAnbieter.gemini();

    @Test
    void neuerSchluesselLiegtNurVerschluesseltInDerDatenbank() {
        einstellungen.speichereSchluessel(mistral, "  test-mistral-123  ");

        String gespeichert = speicher.werte.get("ai.mistral.api-key");
        assertThat(gespeichert).startsWith("v1:").doesNotContain("test-mistral-123");
        assertThat(einstellungen.schluessel(mistral)).isEqualTo("test-mistral-123");
        assertThat(einstellungen.hatSchluessel(mistral)).isTrue();
    }

    @Test
    void geminiSchluesselBleibtWoErImmerLag() {
        einstellungen.speichereSchluessel(gemini, "test-gemini-456");

        assertThat(speicher.werte.get("ai.gemini.api-key")).isEqualTo("test-gemini-456");
        assertThat(einstellungen.schluessel(gemini)).isEqualTo("test-gemini-456");

        einstellungen.entferneSchluessel(gemini);
        assertThat(einstellungen.hatSchluessel(gemini)).isFalse();
    }

    @Test
    void entfernenUeberdecktAuchDenRueckfallAusDenProperties() {
        speicher.umgebung.setProperty("ai.mistral.api-key", "test-aus-properties");
        assertThat(einstellungen.schluessel(mistral)).isEqualTo("test-aus-properties");

        einstellungen.entferneSchluessel(mistral);

        assertThat(einstellungen.schluessel(mistral)).isEmpty();
    }

    @Test
    void unlesbarerSchluesselZaehltAlsKeiner() {
        speicher.werte.put("ai.mistral.api-key", "v1:kaputt");

        assertThat(einstellungen.schluessel(mistral)).isEmpty();
        assertThat(einstellungen.hatSchluessel(mistral)).isFalse();
        // … aber der Zustand bleibt sichtbar, damit kein Ersatz-Anbieter still einspringt.
        assertThat(einstellungen.schluesselZustand(mistral)).isEqualTo(KiEinstellungen.SchluesselZustand.NICHT_LESBAR);
    }

    @Test
    void platzhalterAusDenPropertiesZaehltNichtAlsSchluessel() {
        speicher.umgebung.setProperty("ai.mistral.api-key", "OVERRIDE_IN_LOCAL");

        assertThat(einstellungen.schluessel(mistral)).isEmpty();
        assertThat(einstellungen.schluesselZustand(mistral)).isEqualTo(KiEinstellungen.SchluesselZustand.FEHLT);
    }

    @Test
    void ohneVerschluesselungKeinSpeichernNeuerSchluessel() {
        EinstellungenImSpeicher ohne = new EinstellungenImSpeicher("");

        assertThat(ohne.einstellungen.verschluesselungBereit()).isFalse();
        assertThatThrownBy(() -> ohne.einstellungen.speichereSchluessel(mistral, "test-mistral-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Der Server ist noch nicht für verschlüsselte Zugangsdaten eingerichtet.");
        assertThat(ohne.werte).doesNotContainKey("ai.mistral.api-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"kurz", "mit leerzeichen drin", "<script>alert(1)</script>", "zeilen\numbruch-123"})
    void ungueltigeSchluesselWerdenAbgewiesen(String eingabe) {
        assertThatThrownBy(() -> einstellungen.speichereSchluessel(mistral, eingabe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
    }

    @Test
    void leererSchluesselBrauchtEineEingabe() {
        assertThatThrownBy(() -> einstellungen.speichereSchluessel(mistral, "   "))
                .hasMessage("Bitte einen Schlüssel eintragen.");
        assertThatThrownBy(() -> einstellungen.speichereSchluessel(mistral, "x".repeat(10_001)))
                .hasMessage("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
    }

    @Test
    void auswahlUndErsatzMitStandardwerten() {
        assertThat(einstellungen.gewaehlterAnbieter()).isEqualTo(KiAnbieterTyp.GEMINI);
        assertThat(einstellungen.ersatzErlaubt()).isTrue();

        einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);

        assertThat(einstellungen.gewaehlterAnbieter()).isEqualTo(KiAnbieterTyp.MISTRAL);
        assertThat(einstellungen.ersatzErlaubt()).isFalse();

        speicher.werte.put("ai.anbieter", "UNBEKANNT");
        assertThat(einstellungen.gewaehlterAnbieter()).isEqualTo(KiAnbieterTyp.GEMINI);
    }

    @Test
    void modelleJeStufeUndZurueckAufStandard() {
        einstellungen.speichereModelle(mistral, Map.of("STANDARD", " mistral-large-2512 "));

        assertThat(einstellungen.modell(mistral, KiModellStufe.STANDARD)).isEqualTo("mistral-large-2512");
        assertThat(einstellungen.modell(mistral, KiModellStufe.SCHNELL)).isEqualTo("mistral-schnell");

        Map<String, String> leer = new HashMap<>();
        leer.put("STANDARD", null);
        einstellungen.speichereModelle(mistral, leer);
        assertThat(einstellungen.modell(mistral, KiModellStufe.STANDARD)).isEqualTo("mistral-standard");
    }

    @Test
    void ungueltigeModelleWerdenAbgewiesenOhneDieEingabeZuWiederholen() {
        assertThatThrownBy(() -> einstellungen.speichereModelle(mistral, Map.of("STANDARD", "<script>alert(1)</script>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Modellname für „Standard“ ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -");
        assertThatThrownBy(() -> einstellungen.speichereModelle(mistral, Map.of("STANDARD", "../../v1beta/files")))
                .hasMessageStartingWith("Der Modellname für „Standard“ ist ungültig.");
        assertThatThrownBy(() -> einstellungen.speichereModelle(mistral, Map.of("'; DROP TABLE x; --", "m")))
                .hasMessage("Diese Modellstufe gibt es nicht.");
        // Nichts halb gespeichert
        assertThat(speicher.werte).doesNotContainKey("ai.mistral.modell.standard");
    }

    @Test
    void funktionsWahlSpeichernLesenZuruecksetzen() {
        einstellungen.speichereFunktionsWahl(KiFunktion.DOKUMENT_ANALYSE,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, " mistral-large-latest "));

        assertThat(einstellungen.funktionsWahl(KiFunktion.DOKUMENT_ANALYSE))
                .hasValue(new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "mistral-large-latest"));
        assertThat(speicher.werte).containsEntry("ai.funktion.dokument_analyse.anbieter", "MISTRAL");

        einstellungen.speichereFunktionsWahl(KiFunktion.DOKUMENT_ANALYSE, new KiFunktionsWahl(KiAnbieterTyp.GEMINI, ""));
        assertThat(einstellungen.funktionsWahl(KiFunktion.DOKUMENT_ANALYSE))
                .hasValue(new KiFunktionsWahl(KiAnbieterTyp.GEMINI, null));

        einstellungen.speichereFunktionsWahl(KiFunktion.DOKUMENT_ANALYSE, null);
        assertThat(einstellungen.funktionsWahl(KiFunktion.DOKUMENT_ANALYSE)).isEmpty();
    }

    @Test
    void funktionsWahlMitUngueltigemModell() {
        assertThatThrownBy(() -> einstellungen.speichereFunktionsWahl(KiFunktion.EMAIL_TEXT,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "a b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Modellname ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -");
        assertThat(einstellungen.funktionsWahl(KiFunktion.EMAIL_TEXT)).isEmpty();
    }
}
