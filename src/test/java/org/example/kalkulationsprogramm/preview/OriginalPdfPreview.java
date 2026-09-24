package org.example.kalkulationsprogramm.preview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Local mock-preview adapter, deliberately test-scoped. No Spring application,
 * persistence, mail or remote image loading is started. Layouts belong entirely
 * to the unchanged original BestellungPdfService.
 */
public final class OriginalPdfPreview {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private OriginalPdfPreview() { }

    public static byte[] render(JsonNode request) throws Exception {
        String art = request.path("art").asText();
        long id = request.path("id").asLong(0);
        if (!List.of("bedarf", "bestellung", "preisanfrage").contains(art) || id <= 0) {
            throw new IllegalArgumentException("Gültige PDF-Art und positive ID erforderlich.");
        }
        List<BestellungResponseDto> bestellungen = request.has("bestellungen")
                ? JSON.convertValue(request.get("bestellungen"), new TypeReference<>() { }) : List.of();
        if (bestellungen.size() > 5000) {
            throw new IllegalArgumentException("Maximal 5000 Positionen je Vorschau.");
        }
        // The original supplier renderer groups by this field, including free positions.
        bestellungen.forEach(position -> {
            if (position.getProjektName() == null) position.setProjektName("Ohne Projekt");
        });
        BestellungService bestellungService = mock(BestellungService.class);
        when(bestellungService.findeOffeneBestellungen()).thenReturn(bestellungen);
        FirmeninformationRepository firmaRepo = mock(FirmeninformationRepository.class);
        Firmeninformation firma = new Firmeninformation();
        firma.setFirmenname("Muster Metallbau GmbH");
        firma.setStrasse("Musterstraße 1");
        firma.setPlz("12345");
        firma.setOrt("Musterstadt");
        firma.setEmail("test@example.com");
        when(firmaRepo.findFirmeninformation()).thenReturn(Optional.of(firma));
        PreisanfrageLieferantRepository palRepo = mock(PreisanfrageLieferantRepository.class);
        PreisanfragePositionRepository posRepo = mock(PreisanfragePositionRepository.class);
        if (art.equals("preisanfrage")) {
            JsonNode input = request.path("preisanfrage");
            Preisanfrage pa = new Preisanfrage();
            pa.setId(input.path("id").asLong(id));
            pa.setNummer(input.path("nummer").asText("PA-DEMO"));
            pa.setBauvorhaben(input.path("bauvorhaben").asText(""));
            pa.setNotiz(input.path("notiz").asText(""));
            String frist = input.path("antwortFrist").asText("");
            if (!frist.isBlank()) pa.setAntwortFrist(LocalDate.parse(frist));
            PreisanfrageLieferant pal = new PreisanfrageLieferant();
            pal.setId(id);
            pal.setPreisanfrage(pa);
            pal.setToken(input.path("token").asText("DEMO-" + id));
            List<PreisanfragePosition> positionen = request.has("positionen")
                    ? JSON.convertValue(request.get("positionen"), new TypeReference<>() { }) : List.of();
            if (positionen.size() > 5000) throw new IllegalArgumentException("Maximal 5000 Positionen.");
            when(palRepo.findById(id)).thenReturn(Optional.of(pal));
            when(posRepo.findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId())).thenReturn(positionen);
        }
        BestellungPdfService renderer = new BestellungPdfService(bestellungService,
                mock(DateiSpeicherService.class), new ZeugnisService(mock(KategorieRepository.class)),
                mock(FirmeninformationService.class), firmaRepo, palRepo, posRepo);
        Path pdf = switch (art) {
            case "bedarf" -> renderer.generateBedarfslistePdf(id);
            case "bestellung" -> "projekt".equals(request.path("variante").asText())
                    ? renderer.generatePdfForProjekt(id) : renderer.generatePdfForLieferant(id);
            case "preisanfrage" -> renderer.generatePdfForPreisanfrage(id);
            default -> throw new IllegalArgumentException("Unbekannte PDF-Art.");
        };
        try {
            return Files.readAllBytes(pdf);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8097 : Integer.parseInt(args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"ok\",\"mode\":\"original-pdf-mock\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/render", exchange -> {
            int status = 200;
            byte[] body;
            String contentType = "application/pdf";
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    status = 405;
                    throw new IllegalArgumentException("POST erforderlich.");
                }
                byte[] input = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
                if (input.length > MAX_BODY_BYTES) {
                    status = 413;
                    throw new IllegalArgumentException("Vorschau-Daten sind zu groß.");
                }
                body = render(JSON.readTree(input));
                exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=original-vorschau.pdf");
            } catch (Exception e) {
                if (status == 200) status = 400;
                contentType = "application/json";
                body = JSON.writeValueAsBytes(java.util.Map.of("message", "PDF-Vorschau konnte nicht erstellt werden.",
                        "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Original PDF mock preview: http://127.0.0.1:" + port + "/health");
    }
}
