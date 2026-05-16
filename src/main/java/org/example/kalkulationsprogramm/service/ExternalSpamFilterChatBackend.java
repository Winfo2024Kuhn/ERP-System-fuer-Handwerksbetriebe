package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Optionales externes Backend für den Funnel-Spam-Filter. Spricht einen
 * OpenAI-Chat-Completions-kompatiblen Endpoint an, der über Properties
 * konfiguriert wird. Standardmäßig <b>deaktiviert</b> – die Default-Config
 * lässt URL/Modell/API-Key leer, sodass {@link #isEnabled()} {@code false}
 * liefert und der Spam-Filter auf das lokale Backend zurückfällt.
 *
 * <p>Wer dieses Backend nutzen möchte, hinterlegt URL, Modell und (falls
 * nötig) einen Bearer-API-Key in {@code application-local.properties} bzw.
 * über Umgebungsvariablen – Secrets gehören bewusst nicht in die Datenbank.
 */
@Slf4j
@Service
public class ExternalSpamFilterChatBackend implements SpamFilterChatBackend {

    public static final String ID = "extern";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpointUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    @Autowired
    public ExternalSpamFilterChatBackend(
            ObjectMapper objectMapper,
            @Value("${ai.spamfilter.external.url:}") String endpointUrl,
            @Value("${ai.spamfilter.external.api-key:}") String apiKey,
            @Value("${ai.spamfilter.external.model:}") String model,
            @Value("${ai.spamfilter.external.timeout:30}") int timeoutSeconds) {
        this(objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                endpointUrl, apiKey, model, timeoutSeconds);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    ExternalSpamFilterChatBackend(ObjectMapper objectMapper,
                                  HttpClient httpClient,
                                  String endpointUrl,
                                  String apiKey,
                                  String model,
                                  int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpointUrl = endpointUrl == null ? "" : endpointUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String identifier() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(endpointUrl) && StringUtils.hasText(model);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        if (!isEnabled()) {
            throw new IllegalStateException("Externes Spam-Filter-Backend ist nicht konfiguriert");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.1);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);
        ObjectNode usr = objectMapper.createObjectNode();
        usr.put("role", "user");
        usr.put("content", userMessage);
        messages.add(usr);
        body.set("messages", messages);

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (StringUtils.hasText(apiKey)) {
            rb.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(rb.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            int sc = response.statusCode();
            // Bei Auth-Fehlern keinen Body loggen (könnte Token-Hinweise enthalten);
            // sonst Body auf 200 Zeichen kürzen, um versehentliches PII-Leak zu
            // vermeiden, falls der Provider Request-Inhalte in der Fehlermeldung
            // reflektiert.
            if (sc == 401 || sc == 403) {
                log.warn("[SpamFilter/extern] HTTP {} (Auth)", sc);
            } else {
                String respBody = response.body() == null ? "" : response.body();
                String snippet = respBody.length() > 200 ? respBody.substring(0, 200) + "…" : respBody;
                log.warn("[SpamFilter/extern] HTTP {} – {}", sc, snippet);
            }
            throw new IOException("Externer Chat-Endpoint Fehler: HTTP " + sc);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText("");
            if (StringUtils.hasText(content)) {
                return content;
            }
        }
        return "";
    }
}
