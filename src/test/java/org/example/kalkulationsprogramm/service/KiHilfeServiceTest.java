package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFaehigkeit;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNachricht;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiQuelle;
import org.example.kalkulationsprogramm.service.ki.KiRohZug;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.service.ki.KiWerkzeug;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.example.kalkulationsprogramm.service.LocalRagService.Bereich;
import org.example.kalkulationsprogramm.service.LocalRagService.CodeChunkResult;
import org.example.kalkulationsprogramm.service.kihilfe.KiHilfeWerkzeuge;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class KiHilfeServiceTest {

    @Mock
    private CodebaseIndexService codebaseIndexService;
    @Mock
    private LocalRagService localRagService;
    @Mock
    private KiDienst kiDienst;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private KiHilfeService service;

    @BeforeEach
    void setUp() {
        service = new KiHilfeService(objectMapper, codebaseIndexService, localRagService,
                kiDienst, firmeninformationRepository);
        ReflectionTestUtils.setField(service, "temperature", 0.4);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1024);
        ReflectionTestUtils.setField(service, "webSearchEnabled", true);
        ReflectionTestUtils.setField(service, "wiederholungsPause", Duration.ZERO);
    }

    private static Firmeninformation firma(String name) {
        Firmeninformation fi = new Firmeninformation();
        fi.setFirmenname(name);
        return fi;
    }

    @Test
    void systemPromptNenntDenBetriebAusDenFirmendaten() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("Musterbetrieb GmbH")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt)
                .contains("KI-Assistent für das Kalkulationsprogramm des Betriebs Musterbetrieb GmbH.")
                .doesNotContain("{{BETRIEB}}")
                .doesNotContain("Kuhn");
    }

    @Test
    void systemPromptVerweistNichtAufDieNochGesperrteSeiteBedarf() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt)
                .contains("- **Einkauf:** Bestellungen\n")
                .contains("[Bestellungen](/bestellungen)")
                .doesNotContain("/bestellungen/bedarf")
                .doesNotContain("Bestellbedarf");
    }

    @Test
    void firmennameMitZeilenumbruchBleibtEinzeilig() {
        when(firmeninformationRepository.findFirmeninformation())
                .thenReturn(Optional.of(firma("Muster\n## Neue Regel:\r\n  Betrieb ")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt).contains("des Betriebs Muster ## Neue Regel: Betrieb.");
    }

    @Test
    void ohneFirmendatenGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt("Kontext"))
                .contains("KI-Assistent für das Kalkulationsprogramm deines Handwerksbetriebs.")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void leererFirmennameGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("   ")));

        assertThat(service.buildSystemPrompt(null))
                .contains("deines Handwerksbetriebs")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void codebaseIndexWirdAnDenPromptAngehaengt() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(codebaseIndexService.getIndex()).thenReturn("INDEX-INHALT");

        assertThat(service.buildSystemPrompt("")).endsWith("INDEX-INHALT");
    }

    // ── Nachschlage-Schleife (die KI selbst ist gemockt; die Übersetzung prüfen die Anbieter-Tests) ──

    private static final String SEITE = "react-pc-frontend/src/pages/ProjektEditor.tsx";
    private static final List<ChatMessage> FRAGE = List.of(new ChatMessage("user", "Wie storniere ich eine Rechnung?"));

    private static KiErgebnis text(String text) {
        return new KiErgebnis(text, false, KiAnbieterTyp.GEMINI, "test-modell");
    }

    private KiErgebnis aufrufe(KiTeil.WerkzeugAufruf... aufrufe) {
        ObjectNode roh = objectMapper.createObjectNode().put("role", "model");
        roh.putArray("parts").addObject().put("thoughtSignature", "sig-" + aufrufe[0].id());
        return new KiErgebnis("", false, KiAnbieterTyp.GEMINI, "test-modell", List.of(aufrufe), false,
                List.of(), List.of(), new KiRohZug(KiAnbieterTyp.GEMINI, roh));
    }

    private KiErgebnis aufruf(String id, String name, String args) throws Exception {
        return aufrufe(new KiTeil.WerkzeugAufruf(id, name, objectMapper.readTree(args)));
    }

    private static KiAnbieterFehler fehler(int status) {
        return new KiAnbieterFehler("Google Gemini hat die Anfrage abgelehnt (HTTP " + status + ").", status,
                status == 429 || status >= 500);
    }

    /** Die KI antwortet der Reihe nach: Ergebnisse werden geliefert, Ausnahmen geworfen. */
    private void kiAntwortet(Object... antworten) {
        Deque<Object> reihe = new ArrayDeque<>(List.of(antworten));
        when(kiDienst.sende(eq(KiFunktion.PROGRAMM_HILFE), any(KiAnfrage.class))).thenAnswer(aufruf -> {
            Object naechste = reihe.size() > 1 ? reihe.poll() : reihe.peek();
            if (naechste instanceof RuntimeException fehler) throw fehler;
            return naechste;
        });
    }

    private List<KiAnfrage> gesendeteAnfragen(int anzahl) {
        ArgumentCaptor<KiAnfrage> anfragen = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(kiDienst, times(anzahl)).sende(eq(KiFunktion.PROGRAMM_HILFE), anfragen.capture());
        return anfragen.getAllValues();
    }

    private void kiEingerichtet(boolean mitInternet) {
        when(kiDienst.verfuegbar(KiFunktion.PROGRAMM_HILFE)).thenReturn(true);
        lenient().when(kiDienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WEBSUCHE)).thenReturn(mitInternet);
        lenient().when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
    }

    private void wissenVorhanden() {
        kiEingerichtet(true);
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), any(), any())).thenReturn(List.of(
                new CodeChunkResult(SEITE + "::a", "Kopf", SEITE, "frontend-page", "component", "Teil 1", true)));
        when(localRagService.buildContextFromResults(any())).thenReturn("AUTOMATISCHER-KONTEXT");
    }

    private void ohneWissen() {
        kiEingerichtet(true);
        when(localRagService.isAvailable()).thenReturn(false);
    }

    private static List<String> werkzeugNamen(KiAnfrage anfrage) {
        return anfrage.werkzeuge().stream().map(KiWerkzeug::name).toList();
    }

    @Test
    void ohneWissensbasisAntwortetDieKiDirektMitWebsucheUndQuellen() throws Exception {
        ohneWissen();
        kiAntwortet(new KiErgebnis("Laut DIN EN 1090 ...", false, KiAnbieterTyp.GEMINI, "m", List.of(), false,
                List.of("DIN EN 1090"), List.of(new KiQuelle("DIN", "https://example.org/din"),
                        new KiQuelle("doppelt", "https://example.org/din")), null));
        List<String> live = new ArrayList<>();

        ChatResult result = service.chat(FRAGE, null, live::add);

        assertThat(result.reply()).isEqualTo("Laut DIN EN 1090 ...");
        assertThat(result.sources()).containsExactly(new KiHilfeService.SourceLink("DIN", "https://example.org/din"));
        assertThat(result.schritte()).containsExactly("Sucht im Internet: DIN EN 1090");
        assertThat(live).isEqualTo(result.schritte());
        KiAnfrage anfrage = gesendeteAnfragen(1).get(0);
        assertThat(anfrage.websuche()).isTrue();
        assertThat(anfrage.werkzeuge()).isEmpty();
        assertThat(anfrage.systemAnweisung()).doesNotContain("Selbst nachschlagen").contains("Google-Suche");
        assertThat(anfrage.temperatur()).isEqualTo(0.4);
        assertThat(anfrage.maxAusgabe()).isEqualTo(1024);
        assertThat(anfrage.zeitlimit()).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void kiSchlaegtSelbstNachUndAntwortetDann() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(eq("Storno stornieren"), eq(Bereich.BEDIENUNG), eq(4), any())).thenReturn(List.of(
                new CodeChunkResult(SEITE + "::b", "<Button>Stornieren</Button>", SEITE, "frontend-page", "component",
                        "Teil 2", false)));
        kiAntwortet(
                aufruf("a1", KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"Storno stornieren\",\"hinweis\":\"Wie man storniert\"}"),
                text("Klicke auf Stornieren."));
        List<String> live = new ArrayList<>();

        ChatResult result = service.chat(FRAGE, new PageContext("/projekte/7", "Projekte", null, null, null, null), live::add);

        assertThat(result.reply()).isEqualTo("Klicke auf Stornieren.");
        assertThat(live).containsExactly(KiHilfeService.SCHRITT_ERSTE_SUCHE, "Schaut nach: Wie man storniert");
        assertThat(result.schritte()).isEqualTo(live);
        verify(localRagService).search(eq("Wie storniere ich eine Rechnung?"), eq("Seite: Projekte (/projekte/7)"),
                eq("/projekte/7"));

        List<KiAnfrage> anfragen = gesendeteAnfragen(2);
        KiAnfrage erste = anfragen.get(0);
        assertThat(erste.systemAnweisung()).contains("Selbst nachschlagen", "AUTOMATISCHER-KONTEXT");
        assertThat(werkzeugNamen(erste)).containsExactly(KiHilfeWerkzeuge.SUCHEN, KiHilfeWerkzeuge.LESEN);
        assertThat(erste.werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.AUTO);
        assertThat(erste.websuche()).isTrue();

        List<KiNachricht> verlauf = anfragen.get(1).nachrichten();
        assertThat(verlauf).hasSize(3);
        assertThat(verlauf.get(0).rolle()).isEqualTo(KiNachricht.Rolle.NUTZER);
        assertThat(verlauf.get(0).text()).startsWith("Wie storniere ich eine Rechnung?").contains("Seite: /projekte/7");
        // Der Zug der KI geht samt Rohform (Gedanken-Signatur) zurück
        assertThat(verlauf.get(1).rolle()).isEqualTo(KiNachricht.Rolle.KI);
        assertThat(verlauf.get(1).roh().inhalt().toString()).contains("sig-a1");
        assertThat(verlauf.get(1).werkzeugAufrufe()).extracting(KiTeil.WerkzeugAufruf::id).containsExactly("a1");
        assertThat(verlauf.get(2).rolle()).isEqualTo(KiNachricht.Rolle.WERKZEUG);
        KiTeil.WerkzeugErgebnis antwort = (KiTeil.WerkzeugErgebnis) verlauf.get(2).teile().get(0);
        assertThat(antwort.id()).isEqualTo("a1");
        assertThat(antwort.name()).isEqualTo(KiHilfeWerkzeuge.SUCHEN);
        assertThat(antwort.ergebnis().path("treffer").get(0).path("inhalt").asString("")).contains("Stornieren");
    }

    @Test
    void nachVierRundenMussDieKiAntworten() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        String args = "{\"suchbegriffe\":\"x\",\"hinweis\":\"x\"}";
        kiAntwortet(aufruf("1", KiHilfeWerkzeuge.SUCHEN, args), aufruf("2", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("3", KiHilfeWerkzeuge.SUCHEN, args), aufruf("4", KiHilfeWerkzeuge.SUCHEN, args),
                text("Das weiss ich nicht sicher."));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Das weiss ich nicht sicher.");
        List<KiAnfrage> anfragen = gesendeteAnfragen(KiHilfeService.MAX_NACHSCHLAGE_RUNDEN + 1);
        for (int i = 0; i < KiHilfeService.MAX_NACHSCHLAGE_RUNDEN; i++) {
            assertThat(anfragen.get(i).werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.AUTO);
        }
        KiAnfrage letzte = anfragen.get(KiHilfeService.MAX_NACHSCHLAGE_RUNDEN);
        assertThat(letzte.werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.KEINE);
        assertThat(werkzeugNamen(letzte)).contains(KiHilfeWerkzeuge.SUCHEN);
        assertThat(letzte.nachrichten()).hasSize(1 + 2 * KiHilfeService.MAX_NACHSCHLAGE_RUNDEN);
    }

    @Test
    void ruftDieKiInDerLetztenRundeTrotzdemNurWerkzeugeAufGibtEsEinenFehler() throws Exception {
        wissenVorhanden();
        ReflectionTestUtils.setField(service, "webSearchEnabled", false);
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        String args = "{\"suchbegriffe\":\"x\"}";
        kiAntwortet(aufruf("1", KiHilfeWerkzeuge.SUCHEN, args), aufruf("2", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("3", KiHilfeWerkzeuge.SUCHEN, args), aufruf("4", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("5", KiHilfeWerkzeuge.SUCHEN, args));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessage("Keine Textantwort von der KI erhalten");
        List<KiAnfrage> anfragen = gesendeteAnfragen(5);
        // Websuche abgeschaltet: nur eigene Werkzeuge
        assertThat(anfragen).allSatisfy(anfrage -> assertThat(anfrage.websuche()).isFalse());
        assertThat(anfragen.get(4).werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.KEINE);
    }

    @Test
    void mehrAlsDreiGleichzeitigeAufrufeWerdenNichtAusgefuehrtAberBeantwortet() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        List<KiTeil.WerkzeugAufruf> vier = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            vier.add(new KiTeil.WerkzeugAufruf("c" + i, KiHilfeWerkzeuge.SUCHEN,
                    objectMapper.readTree("{\"suchbegriffe\":\"wort" + i + "\"}")));
        }
        kiAntwortet(aufrufe(vier.toArray(KiTeil.WerkzeugAufruf[]::new)), text("Fertig."));

        service.chat(FRAGE, null);

        verify(localRagService, times(KiHilfeService.MAX_AUFRUFE_JE_RUNDE)).suchen(anyString(), any(), anyInt(), any());
        List<KiTeil> antworten = gesendeteAnfragen(2).get(1).nachrichten().get(2).teile();
        assertThat(antworten).hasSize(4);
        KiTeil.WerkzeugErgebnis vierte = (KiTeil.WerkzeugErgebnis) antworten.get(3);
        assertThat(vierte.id()).isEqualTo("c4");
        assertThat(vierte.ergebnis().path("hinweis").asString("")).startsWith("Zu viele Suchen");
    }

    @Test
    void websuchenWerdenHoechstensDreimalUndOhneDoppelteGemeldet() throws Exception {
        ohneWissen();
        kiAntwortet(new KiErgebnis("ok", false, KiAnbieterTyp.GEMINI, "m", List.of(), false,
                List.of("Wetter Berlin", "Wetter Berlin", " ", "Regen", "Wind", "Sturm"), List.of(), null));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.schritte()).containsExactly("Sucht im Internet: Wetter Berlin", "Sucht im Internet: Regen",
                "Sucht im Internet: Wind");
    }

    @Test
    void scheitertDieErsteSucheAntwortetDieKiTrotzdem() throws Exception {
        kiEingerichtet(true);
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), isNull(), isNull())).thenThrow(new IllegalStateException("kaputt"));
        kiAntwortet(text("Antwort"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Antwort");
        assertThat(result.schritte()).isEmpty();
        assertThat(gesendeteAnfragen(1).get(0).systemAnweisung()).contains("Selbst nachschlagen");
    }

    @Test
    void ohneTrefferDerErstenSucheKeinSchrittUndKeinKontext() throws Exception {
        kiEingerichtet(true);
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), any(), any())).thenReturn(List.of());
        kiAntwortet(text("Antwort"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.schritte()).isEmpty();
        assertThat(gesendeteAnfragen(1).get(0).systemAnweisung()).doesNotContain("Automatisch gefundene Abschnitte");
    }

    @Test
    void beiUeberlastungEinZweiterVersuch() throws Exception {
        ohneWissen();
        kiAntwortet(fehler(503), text("Hat geklappt"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Hat geklappt");
        gesendeteAnfragen(2);
    }

    @Test
    void dauerhafteFehlerWerdenNichtWiederholt() {
        ohneWissen();
        kiAntwortet(fehler(400));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 400");
        gesendeteAnfragen(1);
    }

    @Test
    void zweimalUeberlastetGibtEinenFehler() {
        ohneWissen();
        kiAntwortet(fehler(429), fehler(503));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 503");
    }

    @Test
    void netzfehlerWirdNichtWiederholtUndBleibtEinFehler() {
        ohneWissen();
        kiAntwortet(new KiAnbieterFehler("Google Gemini ist gerade nicht erreichbar.", true, new IOException("weg")));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessage("Google Gemini ist gerade nicht erreichbar.");
        gesendeteAnfragen(1);
    }

    @Test
    void leereAntwortenSindFehler() {
        ohneWissen();
        kiAntwortet(new KiAnbieterFehler("Google Gemini hat keine Antwort geliefert.", 200, false), text(""));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service.chat(FRAGE, null)).hasMessage("Keine Textantwort von der KI erhalten");
    }

    @Test
    void ohneAnbieterOderNachrichtenKeinAufruf() {
        when(kiDienst.verfuegbar(KiFunktion.PROGRAMM_HILFE)).thenReturn(false, true);

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.KeinPassenderAnbieter.class);
        assertThatThrownBy(() -> service.chat(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        verify(kiDienst, never()).sende(any(), any(KiAnfrage.class));
    }

    @Test
    void wirdDieFunktionMittendrinAusgeschaltetKeinPassenderAnbieter() {
        ohneWissen();
        kiAntwortet(new KiNichtVerfuegbar(KiFunktion.PROGRAMM_HILFE, "aus"));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.KeinPassenderAnbieter.class);
    }

    @Test
    void seitenkontextHaengtGekuerztAnDerLetztenFrage() throws Exception {
        ohneWissen();
        kiAntwortet(text("ok"));
        List<ChatMessage> verlauf = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            verlauf.add(new ChatMessage(i % 2 == 0 ? "user" : "assistant", "Nachricht " + i));
        }
        verlauf.add(new ChatMessage("user", "Letzte Frage"));
        PageContext seite = new PageContext("/projekte", "Projekte", "x".repeat(3_000), "Pflichtfeld fehlt", 52.5, 13.4);

        service.chat(verlauf, seite);

        List<KiNachricht> nachrichten = gesendeteAnfragen(1).get(0).nachrichten();
        assertThat(nachrichten).hasSize(20);
        assertThat(nachrichten.get(19).rolle()).isEqualTo(KiNachricht.Rolle.NUTZER);
        String letzte = nachrichten.get(19).text();
        assertThat(letzte).startsWith("Letzte Frage").contains("[Aktueller Bildschirmkontext]", "Titel: Projekte",
                "Fehlermeldungen: Pflichtfeld fehlt", "Breitengrad 52.5", "x".repeat(2_000) + "…")
                .doesNotContain("x".repeat(2_001));
        assertThat(nachrichten.get(18).rolle()).isEqualTo(KiNachricht.Rolle.KI);
    }

    @Test
    void nachfrageNimmtDieVorigeFrageMit() {
        ChatMessage erste = new ChatMessage("user", "Wie storniere ich eine Rechnung?");
        ChatMessage antwort = new ChatMessage("assistant", "So geht es ...");

        assertThat(KiHilfeService.suchfrage(List.of(erste, antwort, new ChatMessage("user", "Und bei Anfragen?"))))
                .isEqualTo("Wie storniere ich eine Rechnung?\nUnd bei Anfragen?");
        String neueFrage = "Wo kann ich bei einem Mitarbeiter die Stundensaetze fuer Wochenenden aendern?";
        assertThat(KiHilfeService.suchfrage(List.of(erste, antwort, new ChatMessage("user", neueFrage))))
                .isEqualTo(neueFrage);
        assertThat(KiHilfeService.suchfrage(List.of(erste))).isEqualTo("Wie storniere ich eine Rechnung?");
        assertThat(KiHilfeService.suchfrage(List.of(antwort))).isEmpty();
    }

    @Test
    void ohneWerkzeugeKeinNachschlageAbschnittImPrompt() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt("Kontext", true))
                .contains("## Selbst nachschlagen", "Nach höchstens " + KiHilfeService.MAX_NACHSCHLAGE_RUNDEN + " Runden",
                        "Nenne NIE Dateinamen")
                .doesNotContain("{{RUNDEN}}");
        assertThat(service.buildSystemPrompt("Kontext")).doesNotContain("## Selbst nachschlagen");
    }

    @Test
    void lehntDieKiDieWerkzeugeAbAntwortetSieOhneNachschlagen() throws Exception {
        wissenVorhanden();
        kiAntwortet(fehler(400), text("Antwort wie frueher"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Antwort wie frueher");
        KiAnfrage rueckfall = gesendeteAnfragen(2).get(1);
        assertThat(rueckfall.websuche()).isTrue();
        assertThat(rueckfall.werkzeuge()).isEmpty();
        assertThat(rueckfall.systemAnweisung()).contains("AUTOMATISCHER-KONTEXT").doesNotContain("Selbst nachschlagen");
        // Der Verlauf beginnt neu, ohne halbe Werkzeug-Runden
        assertThat(rueckfall.nachrichten()).hasSize(1);
    }

    @Test
    void andereAblehnungenGehenOhneSicherheitsnetzDurch() {
        wissenVorhanden();
        kiAntwortet(fehler(403));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 403");
        gesendeteAnfragen(1);
    }

    @Test
    void gesamtfristAbgelaufenOhneAufrufBeiDerKi() {
        ohneWissen();
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ZERO);

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.ZeitlimitErreicht.class);
        verify(kiDienst, never()).sende(any(), any(KiAnfrage.class));
    }

    @Test
    void zeitueberschreitungNachDerFristWirdZumZeitlimit() {
        ohneWissen();
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ofMillis(200));
        when(kiDienst.sende(eq(KiFunktion.PROGRAMM_HILFE), any(KiAnfrage.class))).thenAnswer(aufruf -> {
            KiAnfrage anfrage = aufruf.getArgument(1);
            assertThat(anfrage.zeitlimit()).isLessThanOrEqualTo(Duration.ofMillis(200));
            Thread.sleep(250);
            throw new KiAnbieterFehler("Google Gemini hat nicht rechtzeitig geantwortet.", true,
                    new HttpTimeoutException("zu langsam"));
        });

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.ZeitlimitErreicht.class);
    }

    @Test
    void zeitueberschreitungVorDerFristBleibtEinNormalerFehler() {
        ohneWissen();
        kiAntwortet(new KiAnbieterFehler("Google Gemini hat nicht rechtzeitig geantwortet.", true,
                new HttpTimeoutException("zu langsam")));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void unterbrochenerAufrufBleibtEineUnterbrechung() {
        ohneWissen();
        when(kiDienst.sende(eq(KiFunktion.PROGRAMM_HILFE), any(KiAnfrage.class))).thenAnswer(aufruf -> {
            Thread.currentThread().interrupt();
            throw new KiAnbieterFehler("Der Aufruf bei Google Gemini wurde unterbrochen.", false,
                    new InterruptedException());
        });

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isFalse();
    }

    @Test
    void schliesstDerNutzerDenChatBrichtDieErsteSucheAb() {
        wissenVorhanden();
        IllegalStateException verbindungZu = new IllegalStateException("Browser weg");

        assertThatThrownBy(() -> service.chat(FRAGE, null, schritt -> { throw verbindungZu; })).isSameAs(verbindungZu);
        verify(kiDienst, never()).sende(any(), any(KiAnfrage.class));
    }

    @Test
    void mitWerkzeugenOhneTrefferKeinVollerCodebaseIndex() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt(null, true)).contains("Selbst nachschlagen");
        verify(codebaseIndexService, times(0)).getIndex();
    }

    @Test
    void kaputterWerkzeugAufrufFuehrtZurAntwortOhneWerkzeuge() throws Exception {
        wissenVorhanden();
        kiAntwortet(new KiErgebnis("", false, KiAnbieterTyp.GEMINI, "m", List.of(), true, List.of(), List.of(), null),
                text("Trotzdem eine Antwort"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Trotzdem eine Antwort");
        assertThat(gesendeteAnfragen(2).get(1).werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.KEINE);
    }

    @Test
    void fehltDieZeitFuerDieAntwortWirdNichtMehrNachgeschlagen() throws Exception {
        wissenVorhanden();
        // Weniger als die Reserve fuer die Antwort: gleich die erste Runde muss antworten
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ofSeconds(59));
        kiAntwortet(text("Kurze Antwort"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Kurze Antwort");
        assertThat(gesendeteAnfragen(1).get(0).werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.KEINE);
    }

    // ── Anbieter ohne Internet-Suche (z. B. Mistral) ──

    @Test
    void ohneInternetSucheWeissDieKiDasUndNachschlagenGehtWeiter() throws Exception {
        kiEingerichtet(false);
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), any(), any())).thenReturn(List.of());
        kiAntwortet(text("Antwort aus dem Programm"));

        service.chat(FRAGE, null);

        KiAnfrage anfrage = gesendeteAnfragen(1).get(0);
        assertThat(anfrage.websuche()).isFalse();
        assertThat(werkzeugNamen(anfrage)).containsExactly(KiHilfeWerkzeuge.SUCHEN, KiHilfeWerkzeuge.LESEN);
        assertThat(anfrage.systemAnweisung())
                .contains("KEINEN Zugriff auf das Internet", "Selbst nachschlagen",
                        "Fragen außerhalb des Programms beantwortest du ohne diese Werkzeuge.")
                .doesNotContain("Google", "googleSearch", "Web-Suche", "{{WEB");
    }

    @Test
    void mitInternetSucheBleibtDerPromptWieBisher() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        String prompt = service.buildSystemPrompt("Kontext", true);

        assertThat(prompt).contains("## Web-Suche & Allgemeinwissen", "googleSearch Tool",
                        "- Wenn der user eine Frage außerhalb des Programms stellt, nutze die Web-Suche",
                        "beantwortest du mit der Google-Suche,\n  nicht mit diesen Werkzeugen.")
                .doesNotContain("{{WEB", "KEINEN Zugriff");
    }
}
