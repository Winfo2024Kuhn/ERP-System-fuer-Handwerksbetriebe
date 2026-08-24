package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragDetailDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragSummaryDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragUpsertRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Outbound-HTTP-Client für die interne Beiträge-API der Website
 * (molecular-mercury, {@code src/pages/api/internal/beitraege/**}). Darüber
 * pflegt das ERP die "Aktuelles"-Beiträge der Website (anlegen, bearbeiten,
 * veröffentlichen, Bilder verwalten).
 *
 * <p>Die Basis-URL kommt NICHT aus einer Property, sondern aus den
 * Firmenstammdaten ({@link FirmeninformationService#getFirmeninformation()}).
 * Sie wird bei JEDEM Aufruf frisch ermittelt, nicht im Konstruktor gecacht,
 * damit ein Admin die Website-URL über die Firma-Seite ändern kann, ohne
 * dass das ERP neu gestartet werden muss. Ist kein Wert hinterlegt, wirft der
 * Client eine {@link BeitraegeWebsiteException}, bevor ein HTTP-Call versucht
 * wird.
 *
 * <p>Jede URL wird von vornherein mit abschließendem Slash gebaut, weil die
 * Website mit {@code trailingSlash: 'always'} konfiguriert ist. Ein
 * 301-Redirect auf einen Aufruf ohne Slash würde bei POST/PATCH/DELETE den
 * Body verlieren, da {@link HttpClient} Redirects nicht automatisch mit
 * demselben Body erneut sendet.
 */
@Slf4j
@Service
public class BeitraegeWebsiteClient {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final FirmeninformationService firmeninformationService;
    private final HttpClient httpClient;
    private final String apiToken;

    @Autowired
    public BeitraegeWebsiteClient(ObjectMapper objectMapper,
                                   FirmeninformationService firmeninformationService,
                                   @Value("${website.beitraege.api-token:}") String apiToken) {
        this.objectMapper = objectMapper;
        this.firmeninformationService = firmeninformationService;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Test-Konstruktor mit injizierbarem HttpClient und FirmeninformationService. */
    BeitraegeWebsiteClient(ObjectMapper objectMapper,
                            FirmeninformationService firmeninformationService,
                            HttpClient httpClient,
                            String apiToken) {
        this.objectMapper = objectMapper;
        this.firmeninformationService = firmeninformationService;
        this.httpClient = httpClient;
        this.apiToken = apiToken;
    }

    public List<BeitragSummaryDto> listeAlle() {
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/")
                .GET()
                .build();
        JsonNode root = sendenUndParsen(request);
        try {
            return objectMapper.readerForListOf(BeitragSummaryDto.class)
                    .readValue(root.path("posts"));
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Beitragsliste konnte nicht gelesen werden.", e);
        }
    }

    public BeitragDetailDto hole(long id) {
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/")
                .GET()
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto anlegen(BeitragUpsertRequest req) {
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/")
                .POST(jsonBody(req))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto aktualisieren(long id, BeitragUpsertRequest req) {
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/")
                .method("PATCH", jsonBody(req))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto statusSetzen(long id, String status) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("status", status);
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/status/")
                .POST(jsonBody(body))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto titelbildSetzen(long id, long imageId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("imageId", imageId);
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/titelbild/")
                .POST(jsonBody(body))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto bildHinzufuegen(long id, MultipartFile bild) {
        String boundary = UUID.randomUUID().toString();
        byte[] body;
        try {
            body = multipartBody(boundary, bild);
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Bilddatei konnte nicht gelesen werden.", e);
        }

        HttpRequest request = HttpRequest.newBuilder(uri("/api/internal/beitraege/" + id + "/bilder/"))
                .timeout(READ_TIMEOUT)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto bildLoeschen(long id, long imageId) {
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/bilder/" + imageId + "/")
                .DELETE()
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    public BeitragDetailDto altTextAktualisieren(long id, long imageId, String altText) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("altText", altText);
        HttpRequest request = jsonRequestBuilder("/api/internal/beitraege/" + id + "/bilder/" + imageId + "/")
                .method("PATCH", jsonBody(body))
                .build();
        return beitragAusAntwort(sendenUndParsen(request));
    }

    // --- Hilfsmethoden ---

    /**
     * Ermittelt die Basis-URL frisch aus den Firmenstammdaten (kein Caching im
     * Konstruktor, siehe Klassenkommentar). Ein etwaiger abschließender Slash
     * am hinterlegten Wert wird entfernt, damit beim Anhängen des Pfads kein
     * doppelter Slash entsteht.
     */
    private String ermittleBaseUrl() {
        String website = firmeninformationService.getFirmeninformation().getWebsite();
        if (website == null || website.isBlank()) {
            throw new BeitraegeWebsiteException("Keine Website-URL in den Firmendaten hinterlegt.");
        }
        return website.endsWith("/") ? website.substring(0, website.length() - 1) : website;
    }

    private URI uri(String path) {
        return URI.create(ermittleBaseUrl() + path);
    }

    private HttpRequest.Builder jsonRequestBuilder(String path) {
        return HttpRequest.newBuilder(uri(path))
                .timeout(READ_TIMEOUT)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json");
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Anfrage konnte nicht als JSON aufgebaut werden.", e);
        }
    }

    private JsonNode sendenUndParsen(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Netzwerkfehler beim Aufruf der Website-API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BeitraegeWebsiteException("Aufruf der Website-API wurde unterbrochen.", e);
        }

        if (response.statusCode() >= 400) {
            log.warn("[BeitraegeWebsite] HTTP {} - {}", response.statusCode(), response.body());
            throw new BeitraegeWebsiteException(
                    "Website-API antwortete mit HTTP " + response.statusCode() + ": " + response.body(),
                    response.statusCode());
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Antwort der Website-API ist kein gültiges JSON.", e);
        }
    }

    private BeitragDetailDto beitragAusAntwort(JsonNode root) {
        try {
            return objectMapper.treeToValue(root.path("post"), BeitragDetailDto.class);
        } catch (IOException e) {
            throw new BeitraegeWebsiteException("Beitrag konnte nicht aus der Antwort gelesen werden.", e);
        }
    }

    /**
     * Baut einen minimalen multipart/form-data-Body von Hand, da
     * {@link HttpClient} keinen eingebauten Multipart-BodyPublisher mitbringt.
     * Das Dateifeld muss {@code bild} heißen, so erwartet es die
     * Website-Route {@code [id]/bilder/index.ts}.
     */
    private byte[] multipartBody(String boundary, MultipartFile bild) throws IOException {
        String zeilenumbruch = "\r\n";
        String dateiname = bild.getOriginalFilename() != null ? bild.getOriginalFilename() : "bild";
        String contentType = bild.getContentType() != null ? bild.getContentType() : "application/octet-stream";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + zeilenumbruch).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"bild\"; filename=\"" + dateiname + "\"" + zeilenumbruch)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + zeilenumbruch + zeilenumbruch).getBytes(StandardCharsets.UTF_8));
        out.write(bild.getBytes());
        out.write(zeilenumbruch.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + zeilenumbruch).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
