package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragDetailDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragSummaryDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragUpsertRequest;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeitraegeWebsiteClientTest {

    private static final String BASE_URL = "https://bauschlosserei-kuhn.de";
    private static final String API_TOKEN = "test-token-123";

    private HttpClient mockHttpClient;
    private FirmeninformationService mockFirmeninformationService;
    private BeitraegeWebsiteClient client;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockFirmeninformationService = mock(FirmeninformationService.class);
        client = new BeitraegeWebsiteClient(
                new ObjectMapper(), mockFirmeninformationService, mockHttpClient, API_TOKEN);
    }

    /**
     * Hinterlegt die übergebene Website-URL im gemockten FirmeninformationService,
     * genau wie es der Client bei jedem Aufruf frisch abfragt.
     */
    private void mitWebsiteUrl(String website) {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setWebsite(website);
        when(mockFirmeninformationService.getFirmeninformation()).thenReturn(dto);
    }

    /**
     * Baut eine gemockte HttpResponse VOR der eigentlichen Stubbing-Zeile fertig.
     * Würde man das when(...) für die Response inline als Argument von
     * when(mockHttpClient.send(...)).thenReturn(...) aufrufen, meldet Mockito
     * "UnfinishedStubbingException", weil die when()-Aufrufe für zwei
     * verschiedene Mocks ineinander verschachtelt wären.
     */
    @SuppressWarnings("unchecked")
    private HttpResponse<String> antwort(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private void stelleAntwortBereit(int statusCode, String body) throws Exception {
        HttpResponse<String> response = antwort(statusCode, body);
        when(mockHttpClient.<String>send(any(HttpRequest.class), any())).thenReturn(response);
    }

    @Test
    void listeAlle_erfolgreicheAntwort_wirdKorrektDeserialisiert() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {
                  "posts": [
                    {"id": 1, "slug": "erster-beitrag", "title": "Erster Beitrag",
                     "excerpt": "Kurzfassung", "status": "published",
                     "publishedAt": "2026-01-01T00:00:00.000Z", "coverImagePath": "/uploads/a.jpg"}
                  ]
                }
                """;
        stelleAntwortBereit(200, json);

        List<BeitragSummaryDto> result = client.listeAlle();

        assertThat(result).hasSize(1);
        BeitragSummaryDto beitrag = result.get(0);
        assertThat(beitrag.getId()).isEqualTo(1L);
        assertThat(beitrag.getSlug()).isEqualTo("erster-beitrag");
        assertThat(beitrag.getTitle()).isEqualTo("Erster Beitrag");
        assertThat(beitrag.getStatus()).isEqualTo("published");
    }

    @Test
    void listeAlle_bautUrlAusFirmeninformationServiceMitAbschliessendemSlashUndAuthHeader() throws Exception {
        mitWebsiteUrl(BASE_URL);
        stelleAntwortBereit(200, "{\"posts\": []}");

        client.listeAlle();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer " + API_TOKEN);
    }

    @Test
    void listeAlle_entferntAbschliessendenSlashDerHinterlegtenWebsiteUrl() throws Exception {
        mitWebsiteUrl(BASE_URL + "/");
        stelleAntwortBereit(200, "{\"posts\": []}");

        client.listeAlle();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertThat(captor.getValue().uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/");
    }

    @Test
    void hole_erfolgreicheAntwort_liefertDetailDto() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {
                  "post": {
                    "id": 5, "slug": "detail", "title": "Detail-Beitrag",
                    "excerpt": "Kurz", "status": "draft", "publishedAt": null,
                    "coverImagePath": null, "content": "Volltext", "images": []
                  }
                }
                """;
        stelleAntwortBereit(200, json);

        BeitragDetailDto result = client.hole(5L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getContent()).isEqualTo("Volltext");
    }

    @Test
    void anlegen_sendetPostMitJsonBodyUndAbschliessendemSlash() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 9, "slug": "neu", "title": "Neu", "excerpt": "E",
                 "status": "draft", "publishedAt": null, "coverImagePath": null,
                 "content": "C", "images": []}}
                """;
        stelleAntwortBereit(201, json);

        BeitragUpsertRequest req = new BeitragUpsertRequest();
        req.setTitle("Neu");
        req.setExcerpt("E");
        req.setContent("C");

        BeitragDetailDto result = client.anlegen(req);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/");
        assertThat(result.getId()).isEqualTo(9L);
    }

    @Test
    void aktualisieren_sendetPatchMitJsonBodyUndAbschliessendemSlash() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 4, "slug": "geaendert", "title": "Geaendert", "excerpt": "E2",
                 "status": "draft", "publishedAt": null, "coverImagePath": null,
                 "content": "C2", "images": []}}
                """;
        stelleAntwortBereit(200, json);

        BeitragUpsertRequest req = new BeitragUpsertRequest();
        req.setTitle("Geaendert");
        req.setExcerpt("E2");
        req.setContent("C2");

        BeitragDetailDto result = client.aktualisieren(4L, req);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("PATCH");
        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/4/");
        String bodyText = eingesammelterBodyText(request);
        assertThat(bodyText).contains("\"title\":\"Geaendert\"");
        assertThat(bodyText).contains("\"excerpt\":\"E2\"");
        assertThat(bodyText).contains("\"content\":\"C2\"");
        assertThat(result.getId()).isEqualTo(4L);
    }

    @Test
    void statusSetzen_bautKorrektenPfadMitSlash() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 3, "slug": "s", "title": "T", "excerpt": "E",
                 "status": "published", "publishedAt": "2026-01-01T00:00:00.000Z",
                 "coverImagePath": null, "content": "C", "images": []}}
                """;
        stelleAntwortBereit(200, json);

        client.statusSetzen(3L, "published");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertThat(captor.getValue().uri().toString())
                .isEqualTo(BASE_URL + "/api/internal/beitraege/3/status/");
        assertThat(captor.getValue().method()).isEqualTo("POST");
    }

    @Test
    void titelbildSetzen_sendetPostMitImageIdBodyUndKorrektemPfad() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 6, "slug": "t", "title": "T", "excerpt": "E",
                 "status": "draft", "publishedAt": null, "coverImagePath": "/uploads/y.jpg",
                 "content": "C", "images": []}}
                """;
        stelleAntwortBereit(200, json);

        client.titelbildSetzen(6L, 42L);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/6/titelbild/");
        assertThat(eingesammelterBodyText(request)).isEqualTo("{\"imageId\":42}");
    }

    @Test
    void bildHinzufuegen_sendetMultipartMitFeldnameBild() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 7, "slug": "b", "title": "T", "excerpt": "E",
                 "status": "draft", "publishedAt": null, "coverImagePath": "/uploads/x.jpg",
                 "content": "C", "images": []}}
                """;
        stelleAntwortBereit(201, json);

        MockMultipartFile datei = new MockMultipartFile(
                "bild", "foto.jpg", "image/jpeg", "Bildinhalt".getBytes(StandardCharsets.UTF_8));

        client.bildHinzufuegen(7L, datei);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/7/bilder/");
        String contentType = request.headers().firstValue("Content-Type").orElse("");
        assertThat(contentType).startsWith("multipart/form-data; boundary=");

        String bodyText = eingesammelterBodyText(request);
        assertThat(bodyText).contains("name=\"bild\"");
        assertThat(bodyText).contains("filename=\"foto.jpg\"");
        assertThat(bodyText).contains("Bildinhalt");
    }

    /** Liest den Body eines HttpRequest.BodyPublisher synchron in einen String ein. */
    private String eingesammelterBodyText(HttpRequest request) {
        AtomicReference<String> bodyText = new AtomicReference<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                // wird in diesem Test nicht erwartet
            }

            @Override
            public void onComplete() {
                bodyText.set(out.toString(StandardCharsets.UTF_8));
            }
        });
        return bodyText.get();
    }

    @Test
    void bildLoeschen_sendetDeleteMitKorrektemPfad() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 8, "slug": "d", "title": "T", "excerpt": "E",
                 "status": "draft", "publishedAt": null, "coverImagePath": null,
                 "content": "C", "images": []}}
                """;
        stelleAntwortBereit(200, json);

        client.bildLoeschen(8L, 15L);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("DELETE");
        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/8/bilder/15/");
    }

    @Test
    void altTextAktualisieren_sendetPatchMitAltTextBodyUndKorrektemPfad() throws Exception {
        mitWebsiteUrl(BASE_URL);
        String json = """
                {"post": {"id": 10, "slug": "a", "title": "T", "excerpt": "E",
                 "status": "draft", "publishedAt": null, "coverImagePath": null,
                 "content": "C", "images": []}}
                """;
        stelleAntwortBereit(200, json);

        client.altTextAktualisieren(10L, 20L, "Neuer Alt-Text");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("PATCH");
        assertThat(request.uri().toString()).isEqualTo(BASE_URL + "/api/internal/beitraege/10/bilder/20/");
        assertThat(eingesammelterBodyText(request)).isEqualTo("{\"altText\":\"Neuer Alt-Text\"}");
    }

    @Test
    void httpStatus401_fuehrtZuBeitraegeWebsiteExceptionMitStatusCode() throws Exception {
        mitWebsiteUrl(BASE_URL);
        stelleAntwortBereit(401, "{\"success\": false, \"message\": \"Nicht autorisiert.\"}");

        assertThatThrownBy(() -> client.listeAlle())
                .isInstanceOf(BeitraegeWebsiteException.class)
                .hasMessageContaining("401");

        try {
            client.listeAlle();
        } catch (BeitraegeWebsiteException e) {
            assertThat(e.getStatusCode()).isEqualTo(401);
        }
    }

    @Test
    void httpStatus404_fuehrtZuBeitraegeWebsiteException() throws Exception {
        mitWebsiteUrl(BASE_URL);
        stelleAntwortBereit(404, "{\"success\": false, \"message\": \"Beitrag nicht gefunden.\"}");

        assertThatThrownBy(() -> client.hole(999L))
                .isInstanceOf(BeitraegeWebsiteException.class)
                .hasMessageContaining("404");
    }

    @Test
    void ioException_beimSenden_propagiertAlsBeitraegeWebsiteException() throws Exception {
        mitWebsiteUrl(BASE_URL);
        when(mockHttpClient.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Verbindung fehlgeschlagen"));

        assertThatThrownBy(() -> client.listeAlle())
                .isInstanceOf(BeitraegeWebsiteException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void interruptedException_beimSenden_propagiertAlsBeitraegeWebsiteExceptionUndSetztInterruptFlag() throws Exception {
        mitWebsiteUrl(BASE_URL);
        when(mockHttpClient.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new InterruptedException("unterbrochen"));

        try {
            assertThatThrownBy(() -> client.hole(1L))
                    .isInstanceOf(BeitraegeWebsiteException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Interrupt-Flag zurücksetzen, damit nachfolgende Tests nicht beeinträchtigt werden.
            Thread.interrupted();
        }
    }

    @Test
    void fehlendeWebsiteUrl_null_fuehrtZuExceptionOhneHttpAufruf() throws Exception {
        mitWebsiteUrl(null);

        assertThatThrownBy(() -> client.listeAlle())
                .isInstanceOf(BeitraegeWebsiteException.class)
                .hasMessageContaining("Keine Website-URL in den Firmendaten hinterlegt.");

        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    void fehlendeWebsiteUrl_leererString_fuehrtZuExceptionOhneHttpAufruf() throws Exception {
        mitWebsiteUrl("   ");

        assertThatThrownBy(() -> client.hole(1L))
                .isInstanceOf(BeitraegeWebsiteException.class)
                .hasMessageContaining("Keine Website-URL in den Firmendaten hinterlegt.");

        verify(mockHttpClient, never()).send(any(), any());
    }
}
