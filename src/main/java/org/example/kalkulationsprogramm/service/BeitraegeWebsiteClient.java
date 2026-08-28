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
 * <p>Die Basis-URL kommt vorrangig aus der Property
 * {@code website.beitraege.base-url}. Ist sie leer, greift der Rückfall auf
 * die Firmenstammdaten ({@link FirmeninformationService#getFirmeninformation()}).
 * Der Firmendaten-Fall wird bei JEDEM Aufruf frisch ermittelt, nicht im
 * Konstruktor gecacht, damit ein Admin die Website-URL über die Firma-Seite
 * ändern kann, ohne dass das ERP neu gestartet werden muss. Ist kein Wert
 * hinterlegt, wirft der Client eine {@link BeitraegeWebsiteException}, bevor
 * ein HTTP-Call versucht wird.
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

    /**
     * Content-Types, die an die Website weitergereicht werden dürfen. Deckt
     * sich mit der Whitelist der Website selbst (siehe {@code anfrage.ts},
     * {@code ALLOWED_TYPES}), damit ein clientseitig manipulierter oder
     * schlicht falscher {@code Content-Type} nicht ungefiltert im
     * multipart-Header landet.
     */
    private static final List<String> ERLAUBTE_BILD_CONTENT_TYPES =
            List.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    private final ObjectMapper objectMapper;
    private final FirmeninformationService firmeninformationService;
    private final HttpClient httpClient;
    private final String baseUrlProperty;
    private final String apiToken;

    @Autowired
    public BeitraegeWebsiteClient(ObjectMapper objectMapper,
                                   FirmeninformationService firmeninformationService,
                                   @Value("${website.beitraege.base-url:}") String baseUrlProperty,
                                   @Value("${website.beitraege.api-token:}") String apiToken) {
        this.objectMapper = objectMapper;
        this.firmeninformationService = firmeninformationService;
        this.baseUrlProperty = baseUrlProperty;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Test-Konstruktor mit injizierbarem HttpClient und FirmeninformationService. */
    BeitraegeWebsiteClient(ObjectMapper objectMapper,
                            FirmeninformationService firmeninformationService,
                            HttpClient httpClient,
                            String baseUrlProperty,
                            String apiToken) {
        this.objectMapper = objectMapper;
        this.firmeninformationService = firmeninformationService;
        this.httpClient = httpClient;
        this.baseUrlProperty = baseUrlProperty;
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
     * Ermittelt die Basis-URL der Beitraege-API.
     *
     * <p>Vorrang hat die Property {@code website.beitraege.base-url}. Sie ist
     * das Gegenstueck zum {@code ERP_BASE_URL} der Website und zeigt in der
     * Regel auf deren Tailscale-Adresse. Ist sie leer, greift der Rueckfall
     * auf {@code Firmeninformation.website}.
     *
     * <p>Die Trennung ist noetig, weil {@code Firmeninformation.website} auch
     * als oeffentliche Firmenadresse auf PDF-Exporte gedruckt wird
     * ({@code BelegeKasseExportPdfService}). Dort darf keine interne Adresse
     * stehen.
     *
     * <p>Ein etwaiger abschliessender Slash wird entfernt, damit beim Anhaengen
     * des Pfads kein doppelter Slash entsteht. Verlangt wird zwingend
     * {@code https} (Ausnahme {@code http://localhost} bzw.
     * {@code http://127.0.0.1} fuer die lokale Entwicklung) - sonst ginge der
     * Bearer-Token im Klartext ueber die Leitung. Tailscale liefert ueber
     * MagicDNS echtes HTTPS, die Pruefung steht der VPN-Strecke also nicht im Weg.
     *
     * <p>Eine Website-URL ohne Schema (z. B. {@code www.beispiel.de}) oder mit
     * fehlerhafter Syntax wird ebenfalls hier abgefangen, statt als
     * unbehandelte {@link IllegalArgumentException} erst beim späteren Aufbau
     * des {@link HttpRequest} durchzuschlagen.
     */
    private String ermittleBaseUrl() {
        String website = (baseUrlProperty != null && !baseUrlProperty.isBlank())
                ? baseUrlProperty
                : firmeninformationService.getFirmeninformation().getWebsite();
        if (website == null || website.isBlank()) {
            throw new BeitraegeWebsiteException(
                    "Keine Website-Adresse hinterlegt. Entweder website.beitraege.base-url setzen "
                    + "oder die Website in den Firmendaten eintragen.");
        }
        String basisUrl = website.endsWith("/") ? website.substring(0, website.length() - 1) : website;

        String scheme;
        String host;
        try {
            URI parsed = URI.create(basisUrl);
            scheme = parsed.getScheme();
            host = parsed.getHost();
        } catch (IllegalArgumentException e) {
            throw new BeitraegeWebsiteException("Ungültige Website-URL in den Firmendaten hinterlegt: " + website, e);
        }

        boolean istLokalesHttp = "http".equals(scheme)
                && ("localhost".equals(host) || "127.0.0.1".equals(host));
        if (!"https".equals(scheme) && !istLokalesHttp) {
            throw new BeitraegeWebsiteException(
                    "Website-URL muss https verwenden (oder http://localhost für lokale Entwicklung).");
        }

        return basisUrl;
    }

    /**
     * Wirft eine {@link BeitraegeWebsiteException}, wenn kein API-Token
     * konfiguriert ist. Ohne diese Prüfung würde ein Request mit leerem
     * {@code Authorization: Bearer }-Header verschickt, den die Website mit
     * 401 quittiert &mdash; für den ERP-Nutzer nur schwer von einer
     * tatsächlich nicht erreichbaren Website zu unterscheiden.
     */
    private void pruefeApiToken() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new BeitraegeWebsiteException("Kein API-Token für die Website-Anbindung konfiguriert.");
        }
    }

    private URI uri(String path) {
        pruefeApiToken();
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
     *
     * <p>Dateiname und Content-Type stammen aus dem hochgeladenen
     * {@link MultipartFile} und damit letztlich vom Client. Der Dateiname
     * wird deshalb von Zeichen befreit, mit denen sich aus dem
     * {@code Content-Disposition}-Header ausbrechen ließe, und der
     * Content-Type gegen eine feste Whitelist geprüft, bevor beides in die
     * Header-Zeilen eingebaut wird.
     */
    private byte[] multipartBody(String boundary, MultipartFile bild) throws IOException {
        String zeilenumbruch = "\r\n";
        String dateiname = sanitisiereDateiname(bild.getOriginalFilename());
        String contentType = bild.getContentType() != null ? bild.getContentType() : "application/octet-stream";
        if (!ERLAUBTE_BILD_CONTENT_TYPES.contains(contentType)) {
            throw new BeitraegeWebsiteException("Nicht unterstützter Bildtyp: " + contentType);
        }

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

    /**
     * Entfernt aus einem Dateinamen die Zeichen, mit denen sich aus dem
     * {@code Content-Disposition}-Header ausbrechen ließe (Anführungszeichen,
     * Backslash, Carriage-Return, Newline). Fehlt der Dateiname, wird ein
     * fester Fallback-Name verwendet.
     */
    private static String sanitisiereDateiname(String dateiname) {
        if (dateiname == null || dateiname.isBlank()) {
            return "bild";
        }
        return dateiname.replaceAll("[\"\\\\\r\n]", "_");
    }
}
