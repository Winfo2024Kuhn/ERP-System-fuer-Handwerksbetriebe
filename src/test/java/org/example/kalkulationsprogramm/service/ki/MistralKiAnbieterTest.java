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
            "mistral-small-latest", "mistral-large-latest", "mistral-large-latest", "mistral-ocr-latest");

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
        KiAnfrage anfrage = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "audio/webm")).bauen();

        assertThatThrownBy(() -> anbieter.baueKoerper(anfrage, "m", datei -> ""))
                .isInstanceOf(KiAnbieterFehler.class)
                .hasMessage("Mistral kann diese Dateiart in diesem Programm noch nicht lesen (audio/webm).");
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
                  {"id":"codestral-latest"}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(liste);

        List<KiModellInfo> modelle = anbieter.ladeModelle(SCHLUESSEL);

        assertThat(modelle).filteredOn(KiModellInfo::chat).extracting(KiModellInfo::modell)
                .containsExactly("mistral-large-2512", "codestral-latest");
        assertThat(modelle.get(0).bilder()).isTrue();
        assertThat(modelle.get(0).aliase()).containsExactly("mistral-large-latest");
        assertThat(modelle.get(3).bilder()).isNull();
    }

    @Test
    void testPrueftAuchDieTexterkennung() throws Exception {
        HttpResponse<String> liste = antwort(200, """
                {"data":[{"id":"mistral-large-2512","aliases":["mistral-large-latest"]}]}""");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(liste);

        assertThat(anbieter.teste(SCHLUESSEL, List.of("mistral-large-latest")).message())
                .isEqualTo("Der Schlüssel ist gültig, aber diese Modelle kennt Mistral nicht: mistral-ocr-latest. "
                        + "Bitte die Schreibweise prüfen.");
        assertThat(anbieter.teste("test\nkaputt", List.of()).message())
                .isEqualTo("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
    }

    @Test
    void steckbrief() {
        assertThat(anbieter.typ()).isEqualTo(KiAnbieterTyp.MISTRAL);
        assertThat(anbieter.name()).isEqualTo("Mistral");
        assertThat(anbieter.datenstandort()).isEqualTo(KiDatenstandort.EU);
        assertThat(anbieter.faehigkeiten())
                .containsExactlyInAnyOrder(KiFaehigkeit.TEXT, KiFaehigkeit.BILD, KiFaehigkeit.PDF);
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
