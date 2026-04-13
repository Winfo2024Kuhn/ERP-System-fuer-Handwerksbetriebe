package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

    private OllamaService ollamaService;
    private HttpClient mockHttpClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockHttpClient = mock(HttpClient.class);
        ollamaService = new OllamaService(
                objectMapper, mockHttpClient,
                "http://localhost:11434", "gemma4:e4b", 30, true
        );
    }

    @Test
    void chat_successfulResponse_returnsContent() throws Exception {
        // Ollama /api/chat Response Format
        String ollamaResponse = """
                {
                  "model": "gemma4:e4b",
                  "message": {
                    "role": "assistant",
                    "content": "{\\"key\\": \\"PROJEKT_42\\", \\"confidence\\": 0.85, \\"reason\\": \\"Betreff passt\\"}"
                  },
                  "done": true
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(ollamaResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = ollamaService.chat("Du bist ein Agent.", "Klassifiziere diese Email.");

        assertThat(result).contains("PROJEKT_42");
        assertThat(result).contains("0.85");
    }

    @Test
    void chat_httpError_throwsIOException() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> ollamaService.chat("system", "user"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void chat_emptyResponse_throwsIOException() throws Exception {
        String ollamaResponse = """
                {
                  "model": "gemma4:e4b",
                  "message": {
                    "role": "assistant",
                    "content": ""
                  },
                  "done": true
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(ollamaResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> ollamaService.chat("system", "user"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Leere Antwort");
    }

    @Test
    void chat_disabledService_throwsIllegalState() {
        OllamaService disabled = new OllamaService(
                objectMapper, mockHttpClient,
                "http://localhost:11434", "gemma4:e4b", 30, false
        );

        assertThatThrownBy(() -> disabled.chat("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deaktiviert");
    }

    @Test
    void isAvailable_serverUp_returnsTrue() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertThat(ollamaService.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_serverDown_returnsFalse() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThat(ollamaService.isAvailable()).isFalse();
    }

    @Test
    void isEnabled_returnsConfiguredValue() {
        assertThat(ollamaService.isEnabled()).isTrue();

        OllamaService disabled = new OllamaService(
                objectMapper, mockHttpClient,
                "http://localhost:11434", "gemma4:e4b", 30, false
        );
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void chat_sendsCorrectRequestStructure() throws Exception {
        String ollamaResponse = """
                {
                  "model": "gemma4:e4b",
                  "message": { "role": "assistant", "content": "test reply" },
                  "done": true
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(ollamaResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ollamaService.chat("System-Prompt hier", "User-Nachricht hier");

        // Verify HTTP call was made
        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
