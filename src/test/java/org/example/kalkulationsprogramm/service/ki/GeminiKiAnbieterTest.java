package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Übersetzung der neutralen Anfrage nach Gemini und zurück. Nur Dummy-Daten und Test-Schlüssel. */
class GeminiKiAnbieterTest {

    private static final String SCHLUESSEL = "test-gemini-456";

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final HttpClient http = mock(HttpClient.class);
    private final GeminiKiAnbieter anbieter =
            new GeminiKiAnbieter(mapper, http, "gemini-schnell", "gemini-standard", "gemini-gruendlich");

    @SuppressWarnings("unchecked")
    private void antwortet(int status, String body) throws Exception {
        HttpResponse<String> antwort = mock(HttpResponse.class);
        when(antwort.statusCode()).thenReturn(status);
        when(antwort.body()).thenReturn(body);
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(antwort);
    }

    @Test
    void koerperEnthaeltAnweisungVerlaufDateiUndForm() {
        ObjectNode schema = mapper.createObjectNode().put("type", "OBJECT");
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("Sei knapp.")
                .nachricht(KiNachricht.nutzer("Erste Frage"))
                .nachricht(KiNachricht.ki("Erste Antwort"))
                .nutzer(KiTeil.datei(new byte[] {1, 2, 3}, "application/pdf"), KiTeil.text("Lies das."))
                .json(schema).temperatur(0.0).maxAusgabe(1024).bauen();

        JsonNode koerper = anbieter.baueKoerper(anfrage);

        assertThat(koerper.path("systemInstruction").path("parts").path(0).path("text").asString(""))
                .isEqualTo("Sei knapp.");
        JsonNode contents = koerper.path("contents");
        assertThat(contents.size()).isEqualTo(3);
        assertThat(contents.get(1).path("role").asString("")).isEqualTo("model");
        assertThat(contents.get(2).path("parts").get(0).path("inlineData").path("mimeType").asString(""))
                .isEqualTo("application/pdf");
        assertThat(contents.get(2).path("parts").get(0).path("inlineData").path("data").asString("")).isEqualTo("AQID");
        JsonNode konfiguration = koerper.path("generationConfig");
        assertThat(konfiguration.path("responseMimeType").asString("")).isEqualTo("application/json");
        assertThat(konfiguration.path("responseSchema").path("type").asString("")).isEqualTo("OBJECT");
        assertThat(konfiguration.path("maxOutputTokens").asInt(0)).isEqualTo(1024);
        assertThat(konfiguration.path("temperature").asDouble(1.0)).isZero();
    }

    @Test
    void ohneAnweisungUndOhneJsonNurText() {
        JsonNode koerper = anbieter.baueKoerper(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen());

        assertThat(koerper.has("systemInstruction")).isFalse();
        assertThat(koerper.path("generationConfig").path("responseMimeType").asString("")).isEqualTo("text/plain");
        assertThat(koerper.path("generationConfig").has("temperature")).isFalse();
    }

    @Test
    void antwortOhneGedankenUndMitAbschnittMeldung() throws Exception {
        antwortet(200, """
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[
                  {"text":"Ich denke nach","thought":true},{"text":"{\\"a\\":"},{"text":"1}"}]}}]}""");

        KiErgebnis ergebnis = anbieter.sende(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen(),
                "gemini-standard", SCHLUESSEL);

        assertThat(ergebnis.text()).isEqualTo("{\"a\":1}");
        assertThat(ergebnis.abgeschnitten()).isTrue();
        assertThat(ergebnis.anbieter()).isEqualTo(KiAnbieterTyp.GEMINI);
        ArgumentCaptor<HttpRequest> anfrage = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(anfrage.capture(), any());
        // Der Schlüssel reist im Header, nie in der URL.
        assertThat(anfrage.getValue().uri().toString()).contains("gemini-standard:generateContent")
                .doesNotContain(SCHLUESSEL);
        assertThat(anfrage.getValue().headers().firstValue("x-goog-api-key")).hasValue(SCHLUESSEL);
    }

    @Test
    void keineAntwortIstEinFehlerOhneWiederholung() throws Exception {
        antwortet(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        assertThatThrownBy(() -> anbieter.sende(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen(),
                "m", SCHLUESSEL))
                .isInstanceOfSatisfying(KiAnbieterFehler.class, f -> assertThat(f.voruebergehend()).isFalse())
                .hasMessageContaining("keine Antwort");
    }

    @Test
    void ueberlastungIstVoruebergehendSchluesselfehlerNicht() throws Exception {
        antwortet(429, "{\"error\":\"zu viel\"}");
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen();
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .isInstanceOfSatisfying(KiAnbieterFehler.class, f -> {
                    assertThat(f.voruebergehend()).isTrue();
                    assertThat(f.status()).isEqualTo(429);
                });

        antwortet(403, "{}");
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .isInstanceOfSatisfying(KiAnbieterFehler.class, f -> assertThat(f.voruebergehend()).isFalse())
                .hasMessage("Google Gemini nimmt den Schlüssel nicht an (HTTP 403).");
    }

    @Test
    void netzwerkfehlerUndZeitlimitSindVoruebergehend() throws Exception {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen();
        when(http.<String>send(any(HttpRequest.class), any())).thenThrow(new IOException("weg"));
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .isInstanceOfSatisfying(KiAnbieterFehler.class, f -> assertThat(f.voruebergehend()).isTrue())
                .hasMessage("Google Gemini ist gerade nicht erreichbar.");

        when(http.<String>send(any(HttpRequest.class), any())).thenThrow(new HttpTimeoutException("langsam"));
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .hasMessage("Google Gemini hat nicht rechtzeitig geantwortet.");
    }

    @Test
    void ungueltigeZeichenImSchluesselWerdenNichtZitiert() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen();
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", "test\nschluessel"))
                .isInstanceOf(KiAnbieterFehler.class)
                .hasMessage("Der Schlüssel für Google Gemini enthält ungültige Zeichen.");
    }

    @Test
    void modellListeNurTextmodelleAlsChat() throws Exception {
        antwortet(200, """
                {"models":[
                  {"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash",
                   "supportedGenerationMethods":["generateContent","countTokens"]},
                  {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]},
                  {"name":"models/imagen-4","supportedGenerationMethods":["generateContent"]}]}""");

        List<KiModellInfo> modelle = anbieter.ladeModelle(SCHLUESSEL);

        assertThat(modelle).extracting(KiModellInfo::modell)
                .containsExactly("gemini-3.8-flash", "gemini-embedding-001", "imagen-4");
        assertThat(modelle).filteredOn(KiModellInfo::chat).singleElement().satisfies(m -> {
            assertThat(m.name()).isEqualTo("Gemini 3.8 Flash");
            assertThat(m.bilder()).isTrue();
            // Die Modellliste von Google sagt nichts über Ton: offen statt geraten.
            assertThat(m.ton()).isNull();
        });
    }

    @Test
    void testNenntUnbekannteModelle() throws Exception {
        antwortet(200, "{\"models\":[{\"name\":\"models/gemini-standard\"}]}");

        assertThat(anbieter.teste(SCHLUESSEL, List.of("gemini-standard")).success()).isTrue();
        assertThat(anbieter.teste(SCHLUESSEL, List.of("gemini-standard", "gemini-tippfehler")).message())
                .isEqualTo("Der Schlüssel ist gültig, aber diese Modelle kennt Google Gemini nicht: "
                        + "gemini-tippfehler. Bitte die Schreibweise prüfen.");
        assertThat(anbieter.teste(" ", List.of()).message()).isEqualTo("Kein Schlüssel angegeben.");
        assertThat(anbieter.teste("test\nkaputt", List.of()).message())
                .isEqualTo("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
    }

    // ── Werkzeuge und Google-Suche (Programm-Hilfe, Kostenkonto-Vorschlag) ──

    private KiWerkzeug suchen() {
        ObjectNode parameter = mapper.createObjectNode().put("type", "OBJECT");
        parameter.putObject("properties").putObject("suchbegriffe").put("type", "STRING");
        return new KiWerkzeug("im_programm_suchen", "Sucht im Programm.", parameter);
    }

    private KiAnfrage.Bauer frage() {
        return KiAnfrage.mitAnweisung("Hilf.").nutzer(KiTeil.text("Wie storniere ich eine Rechnung?"));
    }

    @Test
    void werkzeugeMitGoogleSucheWieBisher() {
        JsonNode koerper = anbieter.baueKoerper(frage()
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO).websuche(true).temperatur(0.4).bauen());

        JsonNode tools = koerper.path("tools");
        assertThat(tools.get(0).has("googleSearch")).isTrue();
        JsonNode deklaration = tools.get(1).path("functionDeclarations").get(0);
        assertThat(deklaration.path("name").asString("")).isEqualTo("im_programm_suchen");
        assertThat(deklaration.path("description").asString("")).isEqualTo("Sucht im Programm.");
        assertThat(deklaration.path("parameters").path("type").asString("")).isEqualTo("OBJECT");
        assertThat(koerper.path("toolConfig").path("includeServerSideToolInvocations").asBoolean(false)).isTrue();
        assertThat(koerper.path("toolConfig").has("functionCallingConfig")).isFalse();
        // Mit Werkzeugen kein festes Antwortformat - so schickte es die Programm-Hilfe schon immer.
        assertThat(koerper.path("generationConfig").has("responseMimeType")).isFalse();
        assertThat(koerper.path("generationConfig").path("temperature").asDouble(0)).isEqualTo(0.4);
    }

    @Test
    void letzteRundeErzwingtDieAntwort() {
        JsonNode ohneSuche = anbieter.baueKoerper(frage()
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.KEINE).bauen());
        assertThat(ohneSuche.path("tools").toString()).doesNotContain("googleSearch").contains("im_programm_suchen");
        assertThat(ohneSuche.path("toolConfig").has("includeServerSideToolInvocations")).isFalse();
        assertThat(ohneSuche.path("toolConfig").path("functionCallingConfig").path("mode").asString(""))
                .isEqualTo("NONE");

        JsonNode mitSuche = anbieter.baueKoerper(frage()
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.KEINE).websuche(true).bauen());
        assertThat(mitSuche.path("toolConfig").path("includeServerSideToolInvocations").asBoolean(false)).isTrue();
        assertThat(mitSuche.path("toolConfig").path("functionCallingConfig").path("mode").asString(""))
                .isEqualTo("NONE");
    }

    @Test
    void nurGoogleSucheOhneToolConfigUndEigeneWerkzeugeOhneSucheAuchNicht() {
        JsonNode nurSuche = anbieter.baueKoerper(frage().websuche(true).bauen());
        assertThat(nurSuche.path("tools").toString()).contains("googleSearch").doesNotContain("functionDeclarations");
        assertThat(nurSuche.has("toolConfig")).isFalse();

        JsonNode nurWerkzeuge = anbieter.baueKoerper(frage()
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO).bauen());
        assertThat(nurWerkzeuge.has("toolConfig")).isFalse();
        assertThat(nurWerkzeuge.path("tools")).hasSize(1);
    }

    @Test
    void werkzeugRundeGehtMitSignaturUndIdUnveraendertZurueck() throws Exception {
        antwortet(200, """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"Ich schaue nach."},
                  {"functionCall":{"id":"a1","name":"im_programm_suchen","args":{"suchbegriffe":"Storno"}},
                   "thoughtSignature":"sig-a1"},
                  {"functionCall":{"name":"datei_lesen"}}]},
                  "groundingMetadata":{"webSearchQueries":["DIN EN 1090"],
                    "groundingChunks":[{"web":{"uri":"https://example.org/din","title":""}},
                                       {"web":{"uri":"","title":"leer"}}]}}]}""");
        KiAnfrage erste = frage().werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO).websuche(true).bauen();

        KiErgebnis ergebnis = anbieter.sende(erste, "gemini-standard", SCHLUESSEL);

        assertThat(ergebnis.text()).isEqualTo("Ich schaue nach.");
        assertThat(ergebnis.werkzeugAufrufe()).hasSize(2);
        KiTeil.WerkzeugAufruf aufruf = ergebnis.werkzeugAufrufe().get(0);
        assertThat(aufruf.id()).isEqualTo("a1");
        assertThat(aufruf.name()).isEqualTo("im_programm_suchen");
        assertThat(aufruf.argumente().path("suchbegriffe").asString("")).isEqualTo("Storno");
        assertThat(ergebnis.werkzeugAufrufe().get(1).argumente().isObject()).isTrue();
        assertThat(ergebnis.websuchen()).containsExactly("DIN EN 1090");
        assertThat(ergebnis.quellen()).containsExactly(new KiQuelle("https://example.org/din", "https://example.org/din"));
        assertThat(ergebnis.werkzeugAufrufFehlerhaft()).isFalse();

        KiAnfrage zweite = KiAnfrage.mitAnweisung("Hilf.")
                .nachrichten(erste.nachrichten())
                .nachricht(ergebnis.alsNachricht())
                .nachricht(KiNachricht.werkzeugErgebnisse(List.of(
                        aufruf.ergebnis(mapper.createObjectNode().put("treffer", "Stornieren")),
                        ergebnis.werkzeugAufrufe().get(1).ergebnis(mapper.createArrayNode().add("Liste")))))
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO).bauen();
        JsonNode contents = anbieter.baueKoerper(zweite).path("contents");

        assertThat(contents).hasSize(3);
        // Der Zug des Modells geht unverändert zurück, samt Gedanken-Signatur
        assertThat(contents.get(1).path("role").asString("")).isEqualTo("model");
        assertThat(contents.get(1).path("parts").get(1).path("thoughtSignature").asString("")).isEqualTo("sig-a1");
        JsonNode antworten = contents.get(2).path("parts");
        assertThat(contents.get(2).path("role").asString("")).isEqualTo("user");
        assertThat(antworten.get(0).path("functionResponse").path("id").asString("")).isEqualTo("a1");
        assertThat(antworten.get(0).path("functionResponse").path("name").asString("")).isEqualTo("im_programm_suchen");
        assertThat(antworten.get(0).path("functionResponse").path("response").path("treffer").asString(""))
                .isEqualTo("Stornieren");
        // Ohne id keine id; eine Liste als Antwort wird in ein Objekt gehüllt
        assertThat(antworten.get(1).path("functionResponse").has("id")).isFalse();
        assertThat(antworten.get(1).path("functionResponse").path("response").path("ergebnis").get(0).asString(""))
                .isEqualTo("Liste");
    }

    @Test
    void zugEinesAnderenAnbietersWirdAusDenTeilenGebaut() {
        KiNachricht vonMistral = new KiNachricht(KiNachricht.Rolle.KI, List.of(KiTeil.text("Moment."),
                new KiTeil.WerkzeugAufruf("", "liste_sachkonten", null)),
                new KiRohZug(KiAnbieterTyp.MISTRAL, mapper.createObjectNode().put("role", "assistant")));

        JsonNode zug = anbieter.baueKoerper(frage().nachricht(vonMistral).bauen()).path("contents").get(1);

        assertThat(zug.path("role").asString("")).isEqualTo("model");
        assertThat(zug.path("parts").get(0).path("text").asString("")).isEqualTo("Moment.");
        JsonNode aufruf = zug.path("parts").get(1).path("functionCall");
        assertThat(aufruf.has("id")).isFalse();
        assertThat(aufruf.path("name").asString("")).isEqualTo("liste_sachkonten");
        assertThat(aufruf.path("args").isObject()).isTrue();
    }

    @Test
    void kaputterWerkzeugAufrufUndTextabschnitteMitWerkzeugen() throws Exception {
        antwortet(200, """
                {"candidates":[{"finishReason":"MALFORMED_FUNCTION_CALL","content":{"parts":[
                  {"text":"Gedanke","thought":true},{"text":"Erster Teil"},{"text":"Zweiter Teil"}]}}]}""");

        KiErgebnis ergebnis = anbieter.sende(frage().werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO)
                .bauen(), "m", SCHLUESSEL);

        assertThat(ergebnis.werkzeugAufrufFehlerhaft()).isTrue();
        assertThat(ergebnis.text()).isEqualTo("Erster Teil\nZweiter Teil");
        assertThat(ergebnis.werkzeugAufrufe()).isEmpty();
    }

    @Test
    void zugOhneTeileBleibtEinGueltigerZug() throws Exception {
        antwortet(200, "{\"candidates\":[{\"finishReason\":\"STOP\"}]}");

        KiErgebnis ergebnis = anbieter.sende(frage().bauen(), "m", SCHLUESSEL);

        assertThat(ergebnis.text()).isEmpty();
        assertThat(ergebnis.roh().inhalt().path("parts").isArray()).isTrue();
        assertThat(ergebnis.alsNachricht().teile()).isEmpty();
    }

    @Test
    void steckbrief() {
        assertThat(anbieter.typ()).isEqualTo(KiAnbieterTyp.GEMINI);
        assertThat(anbieter.datenstandort()).isEqualTo(KiDatenstandort.WELTWEIT);
        assertThat(anbieter.faehigkeiten()).containsExactlyInAnyOrder(KiFaehigkeit.values());
        assertThat(anbieter.schluesselVerschluesselt()).isFalse();
        assertThat(anbieter.standardModelle()).containsEntry(KiModellStufe.GRUENDLICH, "gemini-gruendlich");
        assertThat(anbieter.beschreibung()).contains("außerhalb der EU");
    }
}
