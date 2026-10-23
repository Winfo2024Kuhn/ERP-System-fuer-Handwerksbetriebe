package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotRequestDto;
import org.example.kalkulationsprogramm.service.WebsiteAnalyticsSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public S2S-Eingang fuer den taeglichen Analytics-Snapshot der Marketing-
 * Webseite (bauschlosserei-kuhn.de).
 * <p>
 * Auth: laeuft unter der {@code /api/internal/**}-Filterchain in
 * {@link org.example.kalkulationsprogramm.config.SecurityConfig#funnelFilterChain}.
 * Die Website muss den gemeinsamen Schluessel im Header {@code X-ERP-Website-Key}
 * senden ({@link org.example.kalkulationsprogramm.config.WebsiteInternalApiKeyFilter},
 * Property {@code website.internal-api.key}); ohne Schluessel antwortet die Kette mit 401.
 * VPN allein zaehlt nicht als Auth.
 * <p>
 * Spezifikation des Payloads liegt im Website-Repo
 * (docs ANALYTICS_SNAPSHOT_API.md, schemaVersion=1).
 * <p>
 * Wir lesen den Body bewusst als rohen JSON-String ein und parsen erst danach
 * in das DTO. So bleibt {@code raw_payload} byte-identisch zum eingehenden
 * Push - additive Schema-Erweiterungen vom Website-Team gehen nicht verloren.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/analytics-snapshot")
@RequiredArgsConstructor
public class AnalyticsSnapshotIngressController {

    /**
     * Hartes Body-Limit fuer den eingehenden Snapshot. Realistische Payloads
     * liegen im einstelligen KB-Bereich; alles ueber 1 MB ist entweder kaputt
     * oder ein Missbrauchsversuch.
     */
    static final int MAX_BODY_BYTES = 1_000_000;

    private final WebsiteAnalyticsSnapshotService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> empfangeSnapshot(@RequestBody String rawPayload) {
        if (rawPayload != null && rawPayload.length() > MAX_BODY_BYTES) {
            log.warn("Analytics-Snapshot abgelehnt: Payload zu gross ({} Zeichen).", rawPayload.length());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                    "success", false,
                    "message", "Payload zu gross."
            ));
        }

        AnalyticsSnapshotRequestDto dto;
        try {
            dto = objectMapper.readValue(rawPayload, AnalyticsSnapshotRequestDto.class);
        } catch (JacksonException e) {
            log.warn("Analytics-Snapshot Payload ist kein gueltiges JSON: {}", e.getOriginalMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Payload ist kein gueltiges JSON."
            ));
        }

        Set<ConstraintViolation<AnalyticsSnapshotRequestDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            log.warn("Analytics-Snapshot Validierung fehlgeschlagen: {}", details);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Validierung fehlgeschlagen.",
                    "details", details
            ));
        }

        try {
            WebsiteAnalyticsSnapshot saved = service.upsert(dto, rawPayload);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "snapshotDate", saved.getSnapshotDate().toString(),
                    "id", saved.getId()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Analytics-Snapshot abgelehnt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Fehler beim Speichern des Analytics-Snapshots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Interner Fehler."
            ));
        }
    }
}
