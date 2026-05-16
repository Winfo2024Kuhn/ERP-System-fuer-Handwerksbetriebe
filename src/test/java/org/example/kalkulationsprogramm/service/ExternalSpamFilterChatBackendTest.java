package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSpamFilterChatBackendTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isEnabled_falseWhenUrlOrModelMissing() {
        HttpClient http = mock(HttpClient.class);

        var noUrl = new ExternalSpamFilterChatBackend(objectMapper, http, "", "key", "modell", 30);
        var noModel = new ExternalSpamFilterChatBackend(objectMapper, http, "https://example.test/chat", "key", "", 30);
        var blankUrl = new ExternalSpamFilterChatBackend(objectMapper, http, "   ", "", "modell", 30);

        assertThat(noUrl.isEnabled()).isFalse();
        assertThat(noModel.isEnabled()).isFalse();
        assertThat(blankUrl.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_trueWhenUrlAndModelPresent() {
        HttpClient http = mock(HttpClient.class);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "secret", "modell-x", 30);

        assertThat(backend.isEnabled()).isTrue();
        assertThat(backend.identifier()).isEqualTo("extern");
    }

    @Test
    void chat_disabledBackend_throwsIllegalStateException() {
        HttpClient http = mock(HttpClient.class);
        var backend = new ExternalSpamFilterChatBackend(objectMapper, http, "", "", "", 30);

        assertThatThrownBy(() -> backend.chat("sys", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht konfiguriert");
    }

    @Test
    void chat_successfulResponse_returnsContent() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "choices": [
                    {"message": {"role": "assistant", "content": "{\\"spam\\": false, \\"grund\\": \\"ok\\"}"}}
                  ]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "secret", "modell-x", 30);

        String raw = backend.chat("sys-prompt", "user-payload");

        assertThat(raw).contains("\"spam\": false");
    }

    @Test
    void chat_sendsBearerAuthHeader() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(http.send(captor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "secret-key", "modell-x", 30);
        backend.chat("sys", "user");

        HttpRequest req = captor.getValue();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.uri().toString()).isEqualTo("https://example.test/chat");
        assertThat(req.headers().firstValue("Authorization"))
                .contains("Bearer secret-key");
        assertThat(req.headers().firstValue("Content-Type"))
                .contains("application/json");
    }

    @Test
    void chat_omitsAuthHeader_whenApiKeyBlank() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(http.send(captor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "", "modell-x", 30);
        backend.chat("sys", "user");

        assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void chat_httpError_throwsIOException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("upstream down");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "k", "m", 30);

        assertThatThrownBy(() -> backend.chat("sys", "user"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("503");
    }

    @Test
    void chat_emptyChoices_returnsEmptyString() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "k", "m", 30);

        assertThat(backend.chat("sys", "user")).isEmpty();
    }

    @Test
    void identifier_isExtern() {
        var backend = new ExternalSpamFilterChatBackend(objectMapper, mock(HttpClient.class), "", "", "", 30);
        assertThat(backend.identifier()).isEqualTo(ExternalSpamFilterChatBackend.ID);
        assertThat(ExternalSpamFilterChatBackend.ID).isEqualTo("extern");
    }

    @Test
    void responseParsing_passesNestedJsonStringThrough() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"spam\\\":true,\\\"grund\\\":\\\"Test 123\\\"}\"}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var backend = new ExternalSpamFilterChatBackend(
                objectMapper, http, "https://example.test/chat", "k", "m", 30);

        String raw = backend.chat("sys", "user");
        JsonNode parsed = objectMapper.readTree(raw);
        assertThat(parsed.path("spam").asBoolean()).isTrue();
        assertThat(parsed.path("grund").asText()).isEqualTo("Test 123");
    }
}
