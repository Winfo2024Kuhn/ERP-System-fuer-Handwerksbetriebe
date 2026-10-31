package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Übersetzung der neutralen Anfrage nach Mistral und zurück. Nur Dummy-Daten und Test-Schlüssel. */
class MistralKiAnbieterTest {

    private static final String SCHLUESSEL = "test-mistral-123";

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final HttpClient http = mock(HttpClient.class);
    private final MistralKiAnbieter anbieter = new MistralKiAnbieter(mapper, http,
            "mistral-small-latest", "mistral-large-latest", "mistral-large-latest", "mistral-ocr-latest",
            "voxtral-small-latest");

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> antwort(int status, String body) {
        HttpResponse<String> antwort = mock(HttpResponse.class);
        when(antwort.statusCode()).thenReturn(status);
        when(antwort.body()).thenReturn(body);
        return antwort;
    }

    @Test
    void koerperMitSystemVerlaufBildUndXml() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("Sei knapp.")
                .nachricht(KiNachricht.ki("Vorher"))
                .nutzer(KiTeil.datei(new byte[] {1, 2, 3}, "image/png"),
                        KiTeil.datei("<Rechnung/>".getBytes(StandardCharsets.UTF_8), "application/xml"),
                        KiTeil.text("Lies das."))
                .json().temperatur(0.1).maxAusgabe(500).bauen();

        JsonNode koerper = anbieter.baueKoerper(anfrage, "mistral-large-latest", datei -> "nie");

        assertThat(koerper.path("model").asString("")).isEqualTo("mistral-large-latest");
        JsonNode messages = koerper.path("messages");
        assertThat(messages.get(0).path("role").asString("")).isEqualTo("system");
        assertThat(messages.get(1).path("role").asString("")).isEqualTo("assistant");
        assertThat(messages.get(1).path("content").asString("")).isEqualTo("Vorher");
        JsonNode inhalt = messages.get(2).path("content");
        assertThat(inhalt.get(0).path("type").asString("")).isEqualTo("image_url");
        assertThat(inhalt.get(0).path("image_url").asString("")).isEqualTo("data:image/png;base64,AQID");
        assertThat(inhalt.get(1).path("text").asString("")).contains("<Rechnung/>");
        assertThat(inhalt.get(2).path("text").asString("")).isEqualTo("Lies das.");
        assertThat(koerper.path("response_format").path("type").asString("")).isEqualTo("json_object");
        assertThat(koerper.path("max_tokens").asInt(0)).isEqualTo(500);
        assertThat(koerper.path("temperature").asDouble(0)).isEqualTo(0.1);
    }

    @Test
    void grosseAusgabeWirdAufDieObergrenzeGekappt() {
        // Die Dokument-Analyse setzt ihr Gemini-Limit (65.536); Mistral lehnte das bei langen PDFs ab.
        KiAnfrage gross = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).maxAusgabe(65_536).bauen();
        KiAnfrage grenze = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x"))
                .maxAusgabe(MistralKiAnbieter.MAX_AUSGABE).bauen();
        KiAnfrage ohne = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen();

        assertThat(anbieter.baueKoerper(gross, "m", d -> "").path("max_tokens").asInt(0))
                .isEqualTo(MistralKiAnbieter.MAX_AUSGABE).isEqualTo(16_384);
        assertThat(anbieter.baueKoerper(grenze, "m", d -> "").path("max_tokens").asInt(0))
                .isEqualTo(MistralKiAnbieter.MAX_AUSGABE);
        assertThat(anbieter.baueKoerper(ohne, "m", d -> "").has("max_tokens")).isFalse();
    }

    @Test
    void pdfWirdUeberDieTexterkennungGelesen() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("")
                .nutzer(KiTeil.datei(new byte[] {'%', 'P'}, "application/pdf")).bauen();

        JsonNode koerper = anbieter.baueKoerper(anfrage, "m", datei -> "# Rechnung RE-2026-0001");

        assertThat(koerper.path("messages").size()).isEqualTo(1);
        assertThat(koerper.path("messages").get(0).path("content").get(0).path("text").asString(""))
                .startsWith("Inhalt des PDF-Dokuments (per Texterkennung gelesen):")
                .endsWith("# Rechnung RE-2026-0001");
    }

    @Test
    void unbekannteDateiartIstEinFehler() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "video/mp4")).bauen();

        assertThatThrownBy(() -> anbieter.baueKoerper(anfrage, "m", datei -> ""))
                .isInstanceOf(KiAnbieterFehler.class)
                .hasMessage("Mistral kann diese Dateiart in diesem Programm noch nicht lesen (video/mp4).");
    }

    @Test
    void iphoneAufnahmeWirdVerstaendlichAbgelehnt() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "audio/mp4")).bauen();

        assertThatThrownBy(() -> anbieter.baueKoerper(anfrage, "voxtral-small-latest", datei -> ""))
                .isInstanceOf(KiInhaltNichtLesbar.class)
                .hasMessage("Mistral kann dieses Aufnahmeformat nicht lesen (audio/mp4).");
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "audio/mp4"))).isFalse();
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "audio/webm"))).isTrue();
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "image/heic"))).isFalse();
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "image/png"))).isTrue();
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "application/pdf"))).isTrue();
        assertThat(anbieter.liest(new KiTeil.Datei(new byte[] {1}, "application/zip"))).isFalse();
    }

    @Test
    void spracheingabeNenntDieIphoneGrenze() {
        assertThat(anbieter.einschraenkung(KiFunktion.SPRACHEINGABE))
                .hasValue("Aufnahmen vom iPhone (MP4) kann Mistral nicht lesen – sie laufen nur, "
                        + "wenn ein anderer eingerichteter Anbieter einspringen darf.");
        assertThat(anbieter.einschraenkung(KiFunktion.DOKUMENT_ANALYSE)).isEmpty();
    }

    @Test
    void bildformatOhneMistralUnterstuetzungWirdVerstaendlichAbgelehnt() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "image/tiff")).bauen();

        assertThatThrownBy(() -> anbieter.baueKoerper(anfrage, "m", datei -> ""))
                .isInstanceOf(KiAnbieterFehler.class)
                .hasMessage("Mistral kann dieses Bildformat nicht lesen (image/tiff). "
                        + "Bitte als JPG, PNG, WebP oder GIF speichern.");
    }

    @Test
    void bildtypenWerdenVereinheitlicht() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, " image/JPG ")).bauen();

        JsonNode inhalt = anbieter.baueKoerper(anfrage, "m", d -> "").path("messages").get(0).path("content");

        assertThat(inhalt.get(0).path("image_url").asString("")).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void geminiSchemaWirdZuJsonSchema() throws Exception {
        JsonNode gemini = mapper.readTree("""
                {"type":"OBJECT","propertyOrdering":["a"],"properties":{
                  "a":{"type":"STRING","nullable":true},
                  "liste":{"type":"ARRAY","items":{"type":"NUMBER"}}},"required":["a"]}""");
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).json(gemini).bauen();

        JsonNode format = anbieter.baueKoerper(anfrage, "m", d -> "").path("response_format");

        assertThat(format.path("type").asString("")).isEqualTo("json_schema");
        JsonNode schema = format.path("json_schema").path("schema");
        assertThat(schema.path("type").asString("")).isEqualTo("object");
        assertThat(schema.has("propertyOrdering")).isFalse();
        assertThat(schema.path("properties").path("a").path("type").toString()).isEqualTo("[\"string\",\"null\"]");
        assertThat(schema.path("properties").path("liste").path("items").path("type").asString("")).isEqualTo("number");
        assertThat(schema.path("required").get(0).asString("")).isEqualTo("a");
    }

    @Test
    void sendeMitPdfRuftErstTexterkennungDannChat() throws Exception {
        HttpResponse<String> ocr = antwort(200, "{\"pages\":[{\"markdown\":\"Seite 1\"},{\"markdown\":\"Seite 2\"}]}");
        HttpResponse<String> chat = antwort(200, """
                {"choices":[{"finish_reason":"length","message":{"content":[
                  {"type":"thinking","thinking":[]},{"type":"text","text":"{\\"ok\\":true}"}]}}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(ocr, chat);
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("")
                .nutzer(KiTeil.datei(new byte[] {'%', 'P'}, "application/pdf"), KiTeil.text("Lies.")).bauen();

        KiErgebnis ergebnis = anbieter.sende(anfrage, "mistral-large-latest", SCHLUESSEL);

        assertThat(ergebnis.text()).isEqualTo("{\"ok\":true}");
        assertThat(ergebnis.abgeschnitten()).isTrue();
        assertThat(ergebnis.anbieter()).isEqualTo(KiAnbieterTyp.MISTRAL);
        ArgumentCaptor<HttpRequest> anfragen = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(anfragen.capture(), any());
        assertThat(anfragen.getAllValues().get(0).uri().toString()).isEqualTo("https://api.mistral.ai/v1/ocr");
        assertThat(anfragen.getAllValues().get(1).uri().toString())
                .isEqualTo("https://api.mistral.ai/v1/chat/completions");
        assertThat(anfragen.getValue().headers().firstValue("Authorization")).hasValue("Bearer " + SCHLUESSEL);
        assertThat(anfragen.getValue().uri().toString()).doesNotContain(SCHLUESSEL);
        assertThat(koerperVon(anfragen.getAllValues().get(1))).contains("Seite 1\\n\\nSeite 2");
    }

    @Test
    void dieselbePdfWirdNurEinmalDurchDieTexterkennungGeschickt() throws Exception {
        HttpResponse<String> ocr = antwort(200, "{\"pages\":[{\"markdown\":\"Seite 1\"}]}");
        HttpResponse<String> chat = antwort(200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(ocr, chat, chat, ocr, chat);
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1'};
        KiAnfrage runde1 = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(pdf, "application/pdf")).bauen();
        // Runde 2 trägt dieselbe Datei im Verlauf noch einmal (so arbeiten die Werkzeug-Dienste).
        KiAnfrage runde2 = KiAnfrage.mitAnweisung("")
                .nutzer(KiTeil.datei(pdf.clone(), "application/pdf"))
                .nachricht(KiNachricht.ki("Ich schaue nach."))
                .nutzer(KiTeil.text("Und weiter?")).bauen();
        KiAnfrage andere = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {'%', 'P'}, "application/pdf")).bauen();

        anbieter.sende(runde1, "m", SCHLUESSEL);
        anbieter.sende(runde2, "m", SCHLUESSEL);
        anbieter.sende(andere, "m", SCHLUESSEL);

        ArgumentCaptor<HttpRequest> anfragen = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(5)).send(anfragen.capture(), any());
        List<String> pfade = anfragen.getAllValues().stream().map(a -> a.uri().getPath()).toList();
        assertThat(pfade).containsExactly("/v1/ocr", "/v1/chat/completions", "/v1/chat/completions",
                "/v1/ocr", "/v1/chat/completions");
        assertThat(koerperVon(anfragen.getAllValues().get(2))).contains("Seite 1");
    }

    @Test
    void leeresTexterkennungsErgebnisWirdNichtGemerkt() throws Exception {
        HttpResponse<String> leer = antwort(200, "{\"pages\":[]}");
        HttpResponse<String> ocr = antwort(200, "{\"pages\":[{\"markdown\":\"Seite 1\"}]}");
        HttpResponse<String> chat = antwort(200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(leer, chat, ocr, chat);
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("")
                .nutzer(KiTeil.datei(new byte[] {'%', 'P', 'D', 'F'}, "application/pdf")).bauen();

        anbieter.sende(anfrage, "m", SCHLUESSEL);
        anbieter.sende(anfrage, "m", SCHLUESSEL);

        ArgumentCaptor<HttpRequest> anfragen = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).send(anfragen.capture(), any());
        assertThat(anfragen.getAllValues().stream().map(a -> a.uri().getPath()))
                .containsExactly("/v1/ocr", "/v1/chat/completions", "/v1/ocr", "/v1/chat/completions");
    }

    @Test
    void einfacheTextantwort() throws Exception {
        HttpResponse<String> chat = antwort(200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Hallo\"}}]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(chat);

        KiErgebnis ergebnis = anbieter.sende(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen(),
                "m", SCHLUESSEL);

        assertThat(ergebnis.text()).isEqualTo("Hallo");
        assertThat(ergebnis.abgeschnitten()).isFalse();
    }

    @Test
    void fehlerantwortenWerdenVerstaendlich() throws Exception {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen();
        HttpResponse<String> abgelehnt = antwort(401, "{\"message\":\"Unauthorized\"}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(abgelehnt);
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .hasMessage("Mistral nimmt den Schlüssel nicht an (HTTP 401).");

        HttpResponse<String> stoerung = antwort(503, "");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(stoerung);
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .isInstanceOfSatisfying(KiAnbieterFehler.class, f -> assertThat(f.voruebergehend()).isTrue())
                .hasMessage("Mistral hat gerade eine Störung (HTTP 503).");

        HttpResponse<String> leer = antwort(200, "{\"choices\":[]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(leer);
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .hasMessage("Mistral hat keine Antwort geliefert.");

        HttpResponse<String> kaputt = antwort(200, "kein json Max Mustermann");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(kaputt);
        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", SCHLUESSEL))
                .hasMessage("Die Antwort von Mistral war nicht lesbar.");

        assertThatThrownBy(() -> anbieter.sende(anfrage, "m", "test\nschluessel"))
                .hasMessage("Der Schlüssel für Mistral enthält ungültige Zeichen.");
    }

    @Test
    void modellListeMitFaehigkeitenUndAliassen() throws Exception {
        HttpResponse<String> liste = antwort(200, """
                {"data":[
                  {"id":"mistral-large-2512","name":"Mistral Large","aliases":["mistral-large-latest"],
                   "capabilities":{"completion_chat":true,"vision":true}},
                  {"id":"mistral-ocr-latest","capabilities":{"completion_chat":false}},
                  {"id":"mistral-embed","capabilities":{"completion_chat":true}},
                  {"id":"codestral-latest"},
                  {"id":"voxtral-small-2507","capabilities":{"completion_chat":true,"audio":true,"vision":false}},
                  {"id":"voxtral-mini-latest","capabilities":{"completion_chat":true}},
                  {"id":"mistral-medium-2508","capabilities":{"completion_chat":true,"audio":false}}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(liste);

        List<KiModellInfo> modelle = anbieter.ladeModelle(SCHLUESSEL);

        assertThat(modelle).filteredOn(KiModellInfo::chat).extracting(KiModellInfo::modell)
                .containsExactly("mistral-large-2512", "codestral-latest", "voxtral-small-2507",
                        "voxtral-mini-latest", "mistral-medium-2508");
        assertThat(modelle.get(0).bilder()).isTrue();
        assertThat(modelle.get(0).aliase()).containsExactly("mistral-large-latest");
        assertThat(modelle.get(3).bilder()).isNull();
        // Ton: aus capabilities.audio; fehlt das Feld, verrät es nur der Name Voxtral, sonst offen.
        assertThat(modelle).extracting(KiModellInfo::ton)
                .containsExactly(null, null, null, null, true, true, false);
    }

    @Test
    void testPrueftAuchTexterkennungUndSprache() throws Exception {
        HttpResponse<String> liste = antwort(200, """
                {"data":[{"id":"mistral-large-2512","aliases":["mistral-large-latest"]}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(liste);

        assertThat(anbieter.teste(SCHLUESSEL, List.of("mistral-large-latest")).message())
                .isEqualTo("Der Schlüssel ist gültig, aber diese Modelle kennt Mistral nicht: mistral-ocr-latest, "
                        + "voxtral-small-latest. Bitte die Schreibweise prüfen.");
        assertThat(anbieter.teste("test\nkaputt", List.of()).message())
                .isEqualTo("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
    }

    // ── Ton (Spracheingabe) ──

    @Test
    void tonGehtAlsInputAudioAnDenChat() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("Schreib mit.")
                .nutzer(KiTeil.datei(new byte[] {1, 2, 3}, "audio/webm")).temperatur(0.0).bauen();

        JsonNode koerper = anbieter.baueKoerper(anfrage, "voxtral-small-latest", datei -> "nie");

        assertThat(koerper.path("model").asString("")).isEqualTo("voxtral-small-latest");
        JsonNode teil = koerper.path("messages").get(1).path("content").get(0);
        assertThat(teil.path("type").asString("")).isEqualTo("input_audio");
        assertThat(teil.path("input_audio").asString("")).isEqualTo("AQID");
        assertThat(koerper.has("tools")).isFalse();
    }

    @Test
    void fuerTonNimmtMistralDasSprachmodell() {
        assertThat(anbieter.modellFuer(java.util.EnumSet.of(KiFaehigkeit.AUDIO))).hasValue("voxtral-small-latest");
        assertThat(anbieter.modellFuer(java.util.EnumSet.of(KiFaehigkeit.WERKZEUGE, KiFaehigkeit.PDF))).isEmpty();
    }

    // ── Werkzeuge (Programm-Hilfe, Kostenkonto-Vorschlag) ──

    private KiWerkzeug suchen() throws Exception {
        return new KiWerkzeug("im_programm_suchen", "Sucht im Programm.", mapper.readTree("""
                {"type":"OBJECT","properties":{
                  "suchbegriffe":{"type":"STRING"},
                  "bereich":{"type":"STRING","format":"enum","enum":["bedienung","technik"]}},
                 "required":["suchbegriffe"]}"""));
    }

    @Test
    void werkzeugeAlsToolsMitModusUndOhneWebsuche() throws Exception {
        KiAnfrage.Bauer frage = KiAnfrage.mitAnweisung("Hilf.").nutzer(KiTeil.text("Wie storniere ich?"))
                .websuche(true);

        JsonNode auto = anbieter.baueKoerper(frage.werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO)
                .bauen(), "m", d -> "");
        JsonNode funktion = auto.path("tools").get(0);
        assertThat(funktion.path("type").asString("")).isEqualTo("function");
        assertThat(funktion.path("function").path("name").asString("")).isEqualTo("im_programm_suchen");
        assertThat(funktion.path("function").path("description").asString("")).isEqualTo("Sucht im Programm.");
        JsonNode parameter = funktion.path("function").path("parameters");
        assertThat(parameter.path("type").asString("")).isEqualTo("object");
        assertThat(parameter.path("properties").path("bereich").has("format")).isFalse();
        assertThat(parameter.path("properties").path("bereich").path("enum")).hasSize(2);
        assertThat(auto.path("tool_choice").asString("")).isEqualTo("auto");
        // Eine Internet-Suche kann der Chat von Mistral nicht: der Wunsch entfällt still
        assertThat(auto.path("tools")).hasSize(1);
        assertThat(auto.toString()).doesNotContain("web_search");

        JsonNode keine = anbieter.baueKoerper(frage.werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.KEINE)
                .bauen(), "m", d -> "");
        assertThat(keine.path("tool_choice").asString("")).isEqualTo("none");
        assertThat(keine.path("tools")).hasSize(1);
    }

    @Test
    void ohneWerkzeugeKeineTools() {
        JsonNode koerper = anbieter.baueKoerper(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).websuche(true)
                .bauen(), "m", d -> "");

        assertThat(koerper.has("tools")).isFalse();
        assertThat(koerper.has("tool_choice")).isFalse();
    }

    @Test
    void mehrereRundenMitToolCallsUndToolNachrichten() throws Exception {
        HttpResponse<String> runde1 = antwort(200, """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"",
                  "tool_calls":[
                    {"id":"abc123def","type":"function",
                     "function":{"name":"im_programm_suchen","arguments":"{\\"suchbegriffe\\":\\"Storno\\"}"}},
                    {"id":"xyz987uvw","type":"function","function":{"name":"datei_lesen","arguments":{"datei":"a.tsx"}}}]}}]}""");
        HttpResponse<String> runde2 = antwort(200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Klicke auf Stornieren.\"}}]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(runde1, runde2);
        KiAnfrage erste = KiAnfrage.mitAnweisung("Hilf.").nutzer(KiTeil.text("Wie storniere ich?"))
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.AUTO).bauen();

        KiErgebnis ergebnis = anbieter.sende(erste, "mistral-large-latest", SCHLUESSEL);

        assertThat(ergebnis.werkzeugAufrufe()).hasSize(2);
        KiTeil.WerkzeugAufruf aufruf = ergebnis.werkzeugAufrufe().get(0);
        assertThat(aufruf.id()).isEqualTo("abc123def");
        assertThat(aufruf.name()).isEqualTo("im_programm_suchen");
        assertThat(aufruf.argumente().path("suchbegriffe").asString("")).isEqualTo("Storno");
        assertThat(ergebnis.werkzeugAufrufe().get(1).argumente().path("datei").asString("")).isEqualTo("a.tsx");
        assertThat(ergebnis.werkzeugAufrufFehlerhaft()).isFalse();
        assertThat(ergebnis.roh()).isNull();

        KiAnfrage zweite = KiAnfrage.mitAnweisung("Hilf.")
                .nachrichten(erste.nachrichten())
                .nachricht(ergebnis.alsNachricht())
                .nachricht(KiNachricht.werkzeugErgebnisse(List.of(
                        aufruf.ergebnis(mapper.createObjectNode().put("treffer", "Stornieren")),
                        ergebnis.werkzeugAufrufe().get(1).ergebnis(mapper.createObjectNode().put("hinweis", "kurz")))))
                .werkzeuge(List.of(suchen()), KiAnfrage.WerkzeugModus.KEINE).bauen();
        KiErgebnis antwort = anbieter.sende(zweite, "mistral-large-latest", SCHLUESSEL);

        assertThat(antwort.text()).isEqualTo("Klicke auf Stornieren.");
        assertThat(antwort.werkzeugAufrufe()).isEmpty();
        ArgumentCaptor<HttpRequest> anfragen = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(anfragen.capture(), any());
        JsonNode messages = mapper.readTree(koerperVon(anfragen.getAllValues().get(1))).path("messages");
        assertThat(messages).hasSize(5);
        JsonNode kiZug = messages.get(2);
        assertThat(kiZug.path("role").asString("")).isEqualTo("assistant");
        assertThat(kiZug.has("content")).isFalse();
        assertThat(kiZug.path("tool_calls").get(0).path("id").asString("")).isEqualTo("abc123def");
        assertThat(kiZug.path("tool_calls").get(0).path("type").asString("")).isEqualTo("function");
        assertThat(mapper.readTree(kiZug.path("tool_calls").get(0).path("function").path("arguments").asString(""))
                .path("suchbegriffe").asString("")).isEqualTo("Storno");
        JsonNode werkzeug = messages.get(3);
        assertThat(werkzeug.path("role").asString("")).isEqualTo("tool");
        assertThat(werkzeug.path("tool_call_id").asString("")).isEqualTo("abc123def");
        assertThat(werkzeug.path("name").asString("")).isEqualTo("im_programm_suchen");
        assertThat(mapper.readTree(werkzeug.path("content").asString("")).path("treffer").asString(""))
                .isEqualTo("Stornieren");
        assertThat(messages.get(4).path("tool_call_id").asString("")).isEqualTo("xyz987uvw");
    }

    @Test
    void unlesbareArgumenteMeldenEinenKaputtenAufruf() throws Exception {
        HttpResponse<String> chat = antwort(200, """
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"id":"a","function":{"name":"liste_sachkonten","arguments":"{kaputt"}},
                  {"id":"b","function":{"name":"liste_kostenstellen","arguments":""}}]}}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(chat);

        KiErgebnis ergebnis = anbieter.sende(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen(),
                "m", SCHLUESSEL);

        // Wie bei Gemini: Der kaputte Aufruf läuft nicht (auch nicht mit leeren Argumenten), er wird nur gemeldet.
        assertThat(ergebnis.werkzeugAufrufFehlerhaft()).isTrue();
        assertThat(ergebnis.werkzeugAufrufe()).singleElement().satisfies(aufruf -> {
            assertThat(aufruf.name()).isEqualTo("liste_kostenstellen");
            assertThat(aufruf.id()).isEqualTo("b");
            assertThat(aufruf.argumente().isObject()).isTrue();
        });
        assertThat(ergebnis.text()).isEmpty();
    }

    @Test
    void nurKaputteAufrufeErgebenKeineAufrufeUndDieMeldung() throws Exception {
        HttpResponse<String> chat = antwort(200, """
                {"choices":[{"message":{"content":"","tool_calls":[
                  {"id":"a","function":{"name":"finale_zuordnung","arguments":"[1,2]"}}]}}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(chat);

        KiErgebnis ergebnis = anbieter.sende(KiAnfrage.mitAnweisung("").nutzer(KiTeil.text("x")).bauen(),
                "m", SCHLUESSEL);

        assertThat(ergebnis.werkzeugAufrufe()).isEmpty();
        assertThat(ergebnis.werkzeugAufrufFehlerhaft()).isTrue();
        assertThat(ergebnis.alsNachricht().werkzeugAufrufe()).isEmpty();
    }

    @Test
    void kiZugMitTextUndOhneAufrufeBleibtEinfacherText() {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nachricht(KiNachricht.ki("Vorher"))
                .nachricht(new KiNachricht(KiNachricht.Rolle.KI, List.of(KiTeil.text("Ich schaue nach."),
                        new KiTeil.WerkzeugAufruf("id1", "liste_sachkonten", null))))
                .nutzer(KiTeil.text("x")).bauen();

        JsonNode messages = anbieter.baueKoerper(anfrage, "m", d -> "").path("messages");

        assertThat(messages.get(0).path("content").asString("")).isEqualTo("Vorher");
        assertThat(messages.get(0).has("tool_calls")).isFalse();
        assertThat(messages.get(1).path("content").asString("")).isEqualTo("Ich schaue nach.");
        assertThat(messages.get(1).path("tool_calls").get(0).path("function").path("arguments").asString(""))
                .isEqualTo("{}");
    }

    @Test
    void steckbrief() {
        assertThat(anbieter.typ()).isEqualTo(KiAnbieterTyp.MISTRAL);
        assertThat(anbieter.name()).isEqualTo("Mistral");
        assertThat(anbieter.datenstandort()).isEqualTo(KiDatenstandort.EU);
        assertThat(anbieter.faehigkeiten()).containsExactlyInAnyOrder(KiFaehigkeit.TEXT, KiFaehigkeit.BILD,
                KiFaehigkeit.PDF, KiFaehigkeit.AUDIO, KiFaehigkeit.WERKZEUGE);
        assertThat(anbieter.faehigkeiten()).doesNotContain(KiFaehigkeit.WEBSUCHE);
        assertThat(anbieter.schluesselVerschluesselt()).isTrue();
        assertThat(anbieter.standardModelle()).containsEntry(KiModellStufe.SCHNELL, "mistral-small-latest");
        assertThat(anbieter.beschreibung()).contains("EU");
    }

    /** Liest den Körper einer gebauten Anfrage aus. */
    private static String koerperVon(HttpRequest anfrage) {
        StringBuilder text = new StringBuilder();
        anfrage.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription abo) {
                abo.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer teil) {
                byte[] bytes = new byte[teil.remaining()];
                teil.get(bytes);
                text.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable fehler) {
                throw new IllegalStateException(fehler);
            }

            @Override
            public void onComplete() {
                // fertig
            }
        });
        return text.toString();
    }
}
