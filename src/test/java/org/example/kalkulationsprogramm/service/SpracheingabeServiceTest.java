package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiInhaltNichtLesbar;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.dto.SpracheingabeErgebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft vor allem, WAS in der Systemanweisung steht (Metallbau-Begriffe,
 * Reintext-Regel, "[unverstaendlich]" statt Raten) und dass weder die
 * Audio-Bytes noch das Transkript jemals im Klartext einer Fehlermeldung
 * oder eines Logs landen (DSGVO, Sprachaufnahmen von Mitarbeitern im
 * Arbeitsverhaeltnis).
 *
 * DSGVO: ausschliesslich erfundene Dummy-Inhalte, keine echten Aufnahmen.
 */
@ExtendWith(MockitoExtension.class)
class SpracheingabeServiceTest {

    private KiDienst kiDienst;
    private SpracheingabeService service;

    @BeforeEach
    void setUp() {
        kiDienst = mock(KiDienst.class);
        service = new SpracheingabeService(kiDienst);
    }

    private void kiEingerichtet() {
        when(kiDienst.verfuegbar(KiFunktion.SPRACHEINGABE)).thenReturn(true);
    }

    private void kiAntwortet(String text) {
        kiEingerichtet();
        when(kiDienst.sende(Mockito.eq(KiFunktion.SPRACHEINGABE), Mockito.any(KiAnfrage.class)))
                .thenReturn(new KiErgebnis(text, false, KiAnbieterTyp.MISTRAL, "voxtral-small-latest"));
    }

    private static byte[] audioBytes(int laenge) {
        byte[] daten = new byte[laenge];
        Arrays.fill(daten, (byte) 42);
        return daten;
    }

    /**
     * Liefert bis zu {@code gesamtlaenge} Bytes und zaehlt dabei mit, wie viel
     * tatsaechlich gelesen wurde. Damit laesst sich schwarzbox pruefen, DASS
     * der Dienst das Lesen abbricht, statt nur, DASS er am Ende die richtige
     * Ausnahme wirft - eine Implementierung, die erst alles puffert und die
     * Groesse erst danach prueft, wuerde hier viel mehr als
     * {@code MAX_AUDIO_BYTES} Bytes lesen.
     */
    private static final class ZaehlenderStrom extends InputStream {
        private long verbleibend;
        private long gelesen = 0;

        ZaehlenderStrom(long gesamtlaenge) {
            this.verbleibend = gesamtlaenge;
        }

        @Override
        public int read() {
            if (verbleibend <= 0) {
                return -1;
            }
            verbleibend--;
            gelesen++;
            return 42;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (verbleibend <= 0) {
                return -1;
            }
            int anzahl = (int) Math.min(len, verbleibend);
            Arrays.fill(b, off, off + anzahl, (byte) 42);
            verbleibend -= anzahl;
            gelesen += anzahl;
            return anzahl;
        }

        long geleseneBytes() {
            return gelesen;
        }
    }

    @Test
    void systemanweisungEnthaeltDieKernregeln() {
        String anweisung = SpracheingabeService.SYSTEM_ANWEISUNG;

        assertThat(anweisung).contains("[unverstaendlich]");
        assertThat(anweisung).contains("Fuellwoerter");
        assertThat(anweisung).contains("reiner Text");
        assertThat(anweisung).contains("Erfinde nichts dazu");
        assertThat(anweisung).contains("HEB 200");
        // Gliederung des Diktats in feste Abschnitte, leere fallen weg.
        assertThat(anweisung).contains("Erledigt:", "Material verbraucht:", "Probleme / Offen:");
        assertThat(anweisung).contains("Lass einen Abschnitt komplett weg");
        assertThat(anweisung).contains("schreibst du als Stichpunkte ohne Ueberschrift ans Ende.");
        assertThat(anweisung).contains("schreibe nur die Stichpunkte, ohne Ueberschrift.");
        assertThat(anweisung).contains("Lass keine Angabe weg.");
        assertThat(anweisung).contains("Zahlen schreibst du als Ziffern.");
        assertThat(anweisung).contains("Wurde keine Menge genannt, schreibst du keine Zahl hin.");
        assertThat(anweisung).doesNotContain("Ordne den Inhalt nicht um");
        // Reintext-Verbot: eigene Zeile, nicht durch "reiner Text" oben abgedeckt.
        assertThat(anweisung).contains("Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.");
        // Metallbau-Vokabelblock: eigene Zeile, nicht durch "HEB 200" (Profile-Zeile) abgedeckt.
        assertThat(anweisung).contains("Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,");
    }

    @Test
    void anfrageHatGenauEineTondateiUndKeinenText() {
        byte[] audio = {1, 2, 3, 4};

        KiAnfrage anfrage = service.baueAnfrage(audio, "audio/webm");

        assertThat(anfrage.systemAnweisung()).isEqualTo(SpracheingabeService.SYSTEM_ANWEISUNG);
        assertThat(anfrage.nachrichten()).singleElement().satisfies(nachricht -> {
            assertThat(nachricht.teile()).singleElement().isInstanceOfSatisfying(KiTeil.Datei.class, datei -> {
                assertThat(datei.mimeType()).isEqualTo("audio/webm");
                assertThat(datei.daten()).isEqualTo(audio);
            });
        });
    }

    @Test
    void anfrageHatTemperaturNullUndKeinSchemaUndKeineWerkzeuge() {
        KiAnfrage anfrage = service.baueAnfrage(new byte[] {1}, "audio/webm");

        assertThat(anfrage.temperatur()).isEqualTo(0.0);
        assertThat(anfrage.jsonAntwort()).isFalse();
        assertThat(anfrage.schema()).isNull();
        assertThat(anfrage.werkzeuge()).isEmpty();
        assertThat(anfrage.websuche()).isFalse();
        assertThat(anfrage.zeitlimit()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void codecZusatzWirdAbgeschnitten() {
        assertThat(SpracheingabeService.normalisiereInhaltstyp("audio/webm;codecs=opus")).isEqualTo("audio/webm");
        assertThat(SpracheingabeService.normalisiereInhaltstyp(null)).isEqualTo("");
    }

    @Test
    void fremderInhaltstypWirdAbgewiesen() {
        // Absichtlich NICHT-leere Stroeme: Mit einem leeren Stream waere die
        // IllegalArgumentException auch bei einer durchgelassenen Whitelist
        // ueber die "Aufnahme ist leer"-Pruefung gekommen - der Test haette
        // dann gar nichts ueber die Whitelist selbst ausgesagt. Zusaetzlich
        // die konkrete Meldung pruefen, damit nur genau die Whitelist-Pruefung
        // (und keine andere IllegalArgumentException) den Test gruen macht.
        assertThatThrownBy(() -> service.transkribiere(
                new ByteArrayInputStream(audioBytes(10)), "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieses Audioformat wird nicht unterstuetzt.");
        assertThatThrownBy(() -> service.transkribiere(
                new ByteArrayInputStream(audioBytes(10)), "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieses Audioformat wird nicht unterstuetzt.");
    }

    @Test
    void zuGrosseAufnahmeWirdAbgewiesen() {
        kiEingerichtet();
        InputStream zuGross = new ByteArrayInputStream(audioBytes(SpracheingabeService.MAX_AUDIO_BYTES + 1));

        assertThatThrownBy(() -> service.transkribiere(zuGross, "audio/webm"))
                .isInstanceOf(SpracheingabeService.AufnahmeZuGross.class);
    }

    @Test
    void grenzeBrichtDasLesenSofortAbStattAllesVorherZuPuffern() {
        // 5 MB mehr als erlaubt anbieten. Bricht der Dienst NICHT waehrend des
        // Lesens ab, sondern puffert er (fehlerhaft) erst alles und prueft die
        // Groesse erst danach, liest er weit mehr als MAX_AUDIO_BYTES - genau
        // das soll dieser Test aufdecken.
        kiEingerichtet();
        long angebotenGesamt = (long) SpracheingabeService.MAX_AUDIO_BYTES + 5_000_000;
        ZaehlenderStrom strom = new ZaehlenderStrom(angebotenGesamt);

        assertThatThrownBy(() -> service.transkribiere(strom, "audio/webm"))
                .isInstanceOf(SpracheingabeService.AufnahmeZuGross.class);

        assertThat(strom.geleseneBytes())
                .as("der Dienst darf nur knapp ueber die Grenze lesen, nicht die kompletten %d angebotenen Bytes",
                        angebotenGesamt)
                .isGreaterThan((long) SpracheingabeService.MAX_AUDIO_BYTES)
                .isLessThan((long) SpracheingabeService.MAX_AUDIO_BYTES + 1_000_000);
    }

    @Test
    void ohneApiSchluesselFliegtEinEhrlicherFehler() {
        when(kiDienst.verfuegbar(KiFunktion.SPRACHEINGABE)).thenReturn(false);
        ZaehlenderStrom audio = new ZaehlenderStrom(10);

        assertThatThrownBy(() -> service.transkribiere(audio, "audio/webm"))
                .isInstanceOf(SpracheingabeService.KiNichtEingerichtet.class);

        assertThat(audio.geleseneBytes())
                .as("ohne Schluessel wird die Aufnahme gar nicht erst gelesen")
                .isZero();
        Mockito.verify(kiDienst, Mockito.never()).sende(Mockito.any(), Mockito.any(KiAnfrage.class));
    }

    @Test
    void leereAufnahmeWirdMitSchluesselAbgewiesen() {
        kiEingerichtet();

        assertThatThrownBy(() -> service.transkribiere(new ByteArrayInputStream(new byte[0]), "audio/webm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Die Aufnahme ist leer.");

        Mockito.verify(kiDienst, Mockito.never()).sende(Mockito.any(), Mockito.any(KiAnfrage.class));
    }

    @Test
    void ohneApiSchluesselIstDieSpracheingabeAus() {
        when(kiDienst.verfuegbar(KiFunktion.SPRACHEINGABE)).thenReturn(false);

        assertThat(service.istVerfuegbar()).isFalse();
    }

    @Test
    void mitApiSchluesselIstDieSpracheingabeAn() {
        when(kiDienst.verfuegbar(KiFunktion.SPRACHEINGABE)).thenReturn(true);

        assertThat(service.istVerfuegbar()).isTrue();
    }

    @Test
    void anbieterFehlerWirdZuNeutralerMeldung() {
        kiEingerichtet();
        when(kiDienst.sende(Mockito.eq(KiFunktion.SPRACHEINGABE), Mockito.any(KiAnfrage.class)))
                .thenThrow(new KiAnbieterFehler("Mistral hat gerade eine Störung (HTTP 500).", 500, true));
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        // Die Meldung des Anbieters enthaelt nie Inhalte (KiAnbieterFehler); der Dienst gibt
        // trotzdem nur einen festen Satz weiter.
        assertThatThrownBy(() -> service.transkribiere(audio, "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Die Spracherkennung hat mit einem Fehler geantwortet.");
    }

    @Test
    void nichtLesbaresFormatKommtMitKlartextBeimNutzerAn() {
        kiEingerichtet();
        when(kiDienst.sende(Mockito.eq(KiFunktion.SPRACHEINGABE), Mockito.any(KiAnfrage.class)))
                .thenThrow(new KiInhaltNichtLesbar("Mistral kann dieses Aufnahmeformat nicht lesen (audio/mp4). "
                        + "Für die Spracheingabe bitte Google Gemini wählen (Einstellungen → KI-Funktionen)."));

        // Dauerhaft statt „gerade nicht erreichbar“: 400 mit dem Grund (nur Anbieter und Format).
        assertThatThrownBy(() -> service.transkribiere(new ByteArrayInputStream(audioBytes(10)), "audio/mp4"))
                .isInstanceOf(SpracheingabeService.FormatNichtUnterstuetzt.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aufnahmeformat");
    }

    @Test
    void wirdDieFunktionMittendrinAusgeschaltetIstSieNichtEingerichtet() {
        kiEingerichtet();
        when(kiDienst.sende(Mockito.eq(KiFunktion.SPRACHEINGABE), Mockito.any(KiAnfrage.class)))
                .thenThrow(new KiNichtVerfuegbar(KiFunktion.SPRACHEINGABE, "aus"));

        assertThatThrownBy(() -> service.transkribiere(new ByteArrayInputStream(audioBytes(10)), "audio/webm"))
                .isInstanceOf(SpracheingabeService.KiNichtEingerichtet.class);
    }

    @Test
    void antwortTextKommtGetrimmtZurueck() {
        kiAntwortet(" Gelaender montiert.\n");
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        SpracheingabeErgebnis ergebnis = service.transkribiere(audio, "audio/webm;codecs=opus");

        assertThat(ergebnis.text()).isEqualTo("Gelaender montiert.");
        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        Mockito.verify(kiDienst).sende(Mockito.eq(KiFunktion.SPRACHEINGABE), anfrage.capture());
        assertThat(anfrage.getValue().dateien()).singleElement()
                .satisfies(datei -> assertThat(datei.mimeType()).isEqualTo("audio/webm"));
    }
}
