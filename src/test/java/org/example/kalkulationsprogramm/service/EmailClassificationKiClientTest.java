package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.example.kalkulationsprogramm.service.ki.KiAnbieter;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiZuordnung;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Die E-Mail-Zuordnung spricht nur über {@link KiDienst} mit der KI. Nur Dummy-Daten. */
class EmailClassificationKiClientTest {

    private final KiDienst kiDienst = mock(KiDienst.class);
    private final EmailClassificationKiClient client = new EmailClassificationKiClient(kiDienst);

    @Test
    void isEnabledFolgtDerZuordnungDerEmailZuordnung() {
        when(kiDienst.verfuegbar(KiFunktion.EMAIL_ZUORDNUNG)).thenReturn(true, false);

        assertThat(client.isEnabled()).isTrue();
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void anbieterNenntDenKurznamenDesLaufendenAnbietersOderKeiner() {
        KiAnbieter mistral = mock(KiAnbieter.class);
        when(mistral.typ()).thenReturn(KiAnbieterTyp.MISTRAL);
        when(kiDienst.zuordnung(KiFunktion.EMAIL_ZUORDNUNG)).thenReturn(
                new KiZuordnung(KiFunktion.EMAIL_ZUORDNUNG, KiZuordnung.Status.ERSATZ, mistral, null, "Gemini ohne Schlüssel"),
                new KiZuordnung(KiFunktion.EMAIL_ZUORDNUNG, KiZuordnung.Status.AUS, null, null, "Kein Anbieter"));

        assertThat(client.anbieter()).isEqualTo("mistral");
        assertThat(client.anbieter()).isEqualTo("keiner");
    }

    @Test
    void chatSchicktSystemtextUndNachrichtAlsJsonAnfrageMitNiedrigerTemperatur() throws IOException {
        when(kiDienst.sende(eq(KiFunktion.EMAIL_ZUORDNUNG), any()))
                .thenReturn(new KiErgebnis("{\"projektId\":7}", false, KiAnbieterTyp.GEMINI, "g"));

        String antwort = client.chat("Du ordnest E-Mails zu.", "Betreff: Angebot Zaun Max Mustermann");

        assertThat(antwort).isEqualTo("{\"projektId\":7}");
        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(kiDienst).sende(eq(KiFunktion.EMAIL_ZUORDNUNG), anfrage.capture());
        KiAnfrage gesendet = anfrage.getValue();
        assertThat(gesendet.systemAnweisung()).isEqualTo("Du ordnest E-Mails zu.");
        assertThat(gesendet.nachrichten()).singleElement()
                .satisfies(n -> assertThat(n.text()).isEqualTo("Betreff: Angebot Zaun Max Mustermann"));
        assertThat(gesendet.jsonAntwort()).isTrue();
        assertThat(gesendet.temperatur()).isEqualTo(0.1);
        assertThat(gesendet.zeitlimit()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void keinAnbieterWirdZurIOExceptionMitDemHinweis() {
        when(kiDienst.sende(eq(KiFunktion.EMAIL_ZUORDNUNG), any()))
                .thenThrow(new KiNichtVerfuegbar(KiFunktion.EMAIL_ZUORDNUNG, "Für diese Funktion ist kein Anbieter eingerichtet."));

        assertThatThrownBy(() -> client.chat("s", "n"))
                .isInstanceOf(IOException.class)
                .hasMessage("Für diese Funktion ist kein Anbieter eingerichtet.")
                .hasCauseInstanceOf(KiNichtVerfuegbar.class);
    }

    @Test
    void anbieterFehlerWirdZurIOExceptionMitUrsache() {
        when(kiDienst.sende(eq(KiFunktion.EMAIL_ZUORDNUNG), any()))
                .thenThrow(new KiAnbieterFehler("Der KI-Anbieter ist gerade überlastet.", 429, true));

        assertThatThrownBy(() -> client.chat("s", "n"))
                .isInstanceOf(IOException.class)
                .hasMessage("Der KI-Anbieter ist gerade überlastet.")
                .hasCauseInstanceOf(KiAnbieterFehler.class);
    }
}
