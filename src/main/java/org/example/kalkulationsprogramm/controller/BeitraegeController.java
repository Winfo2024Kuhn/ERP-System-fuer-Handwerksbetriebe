package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragDetailDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragSummaryDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragUpsertRequest;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteClient;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Frontend-zugewandter REST-Controller für die Verwaltung der Website-Beiträge
 * ("Aktuelles"-Bereich von bauschlosserei-kuhn.de). Wird vom ERP-Frontend
 * (Teil C dieses Vorhabens) aufgerufen und delegiert jede Methode an
 * {@link BeitraegeWebsiteClient}, der intern die Beiträge-API der Website
 * anspricht.
 *
 * <p>Kein {@code DELETE /{id}} auf Beitragsebene, konsistent zur
 * Website-API aus Teil A: ein ganzer Beitrag kann dort bewusst nicht
 * gelöscht werden.
 *
 * <p>Läuft unter der bestehenden {@code apiFilterChain}
 * ({@code @Order(3)}, {@code securityMatcher("/api/**")} in
 * {@code SecurityConfig}) — Session-Auth + CSRF via {@code X-XSRF-TOKEN}
 * ist damit automatisch aktiv. Die admin-only-Einschränkung selbst kommt
 * erst mit Task B5 hinzu.
 */
@Slf4j
@RestController
@RequestMapping("/api/beitraege")
@RequiredArgsConstructor
public class BeitraegeController {

    private final BeitraegeWebsiteClient beitraegeWebsiteClient;

    @GetMapping
    public ResponseEntity<?> liste() {
        List<BeitragSummaryDto> beitraege = beitraegeWebsiteClient.listeAlle();
        return ResponseEntity.ok(beitraege);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.hole(id);
        return ResponseEntity.ok(beitrag);
    }

    @PostMapping
    public ResponseEntity<?> anlegen(@Valid @RequestBody BeitragUpsertRequest request) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.anlegen(request);
        return ResponseEntity.ok(beitrag);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> aktualisieren(@PathVariable long id, @Valid @RequestBody BeitragUpsertRequest request) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.aktualisieren(id, request);
        return ResponseEntity.ok(beitrag);
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable long id, @RequestBody StatusRequest request) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.statusSetzen(id, request.status());
        return ResponseEntity.ok(beitrag);
    }

    @PostMapping(value = "/{id}/bilder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bildHinzufuegen(@PathVariable long id, @RequestParam("bild") MultipartFile bild) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.bildHinzufuegen(id, bild);
        return ResponseEntity.ok(beitrag);
    }

    @DeleteMapping("/{id}/bilder/{imageId}")
    public ResponseEntity<?> bildLoeschen(@PathVariable long id, @PathVariable long imageId) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.bildLoeschen(id, imageId);
        return ResponseEntity.ok(beitrag);
    }

    @PatchMapping("/{id}/bilder/{imageId}")
    public ResponseEntity<?> altText(@PathVariable long id, @PathVariable long imageId,
                                      @RequestBody AltTextRequest request) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.altTextAktualisieren(id, imageId, request.altText());
        return ResponseEntity.ok(beitrag);
    }

    @PostMapping("/{id}/titelbild")
    public ResponseEntity<?> titelbild(@PathVariable long id, @RequestBody TitelbildRequest request) {
        BeitragDetailDto beitrag = beitraegeWebsiteClient.titelbildSetzen(id, request.imageId());
        return ResponseEntity.ok(beitrag);
    }

    /**
     * Fängt jeden Fehlschlag von {@link BeitraegeWebsiteClient} ab (Netzwerkfehler
     * oder eine Fehlerantwort der Website-API) und übersetzt ihn einheitlich in
     * HTTP 502, statt ihn als 500 durchfallen zu lassen. Nur für Methoden dieses
     * Controllers wirksam, da der Handler hier lokal und nicht als
     * {@code @ControllerAdvice} deklariert ist.
     */
    @ExceptionHandler(BeitraegeWebsiteException.class)
    public ResponseEntity<Map<String, Object>> handleBeitraegeWebsiteException(BeitraegeWebsiteException ex) {
        log.warn("[BeitraegeController] Aufruf der Website-API fehlgeschlagen: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "message", "Website nicht erreichbar oder hat einen Fehler gemeldet."));
    }

    public record StatusRequest(String status) {}

    public record TitelbildRequest(long imageId) {}

    public record AltTextRequest(String altText) {}
}
