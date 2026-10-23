package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.example.kalkulationsprogramm.service.AnfrageFunnelService;
import org.example.kalkulationsprogramm.service.FunnelAnfrageAbgelehntException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * S2S-Endpoint für den Anfrage-Funnel der Marketing-Webseite. Geschützt durch den
 * gemeinsamen Schlüssel {@code X-ERP-Website-Key} (siehe {@code WebsiteInternalApiKeyFilter})
 * bzw. den Cloudflare-Access-JWT; ohne beides antwortet die Kette mit 401.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/anfrage")
@RequiredArgsConstructor
public class AnfrageFunnelController {

    private final AnfrageFunnelService anfrageFunnelService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Multipart-Eingang: JSON-Payload als Part {@code anfrage}, Bilder als Parts
     * {@code bilder} (optional, mehrfach). Der Payload kommt als Text und wird hier
     * von Hand gelesen, darum greift {@code @Valid} nicht – die Pflichtfelder werden
     * explizit geprüft (sonst endet eine unvollständige Anfrage als 500).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> empfangeFunnelAnfrage(
            @RequestPart("anfrage") String anfrageJson,
            @RequestPart(value = "bilder", required = false) List<MultipartFile> bilder) {
        AnfrageFunnelRequestDto dto;
        try {
            dto = objectMapper.readValue(anfrageJson, AnfrageFunnelRequestDto.class);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Anfrage-Payload ist kein gültiges JSON."));
        }
        if (dto == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Anfrage-Payload fehlt."));
        }
        Set<ConstraintViolation<AnfrageFunnelRequestDto>> verstoesse = validator.validate(dto);
        if (!verstoesse.isEmpty()) {
            // Nur Feldnamen loggen, nie die eingegebenen Werte (personenbezogen)
            List<String> felder = verstoesse.stream()
                    .map(v -> v.getPropertyPath().toString())
                    .distinct()
                    .sorted()
                    .toList();
            log.warn("Funnel-Anfrage abgelehnt, ungültige Felder: {}", felder);
            String meldung = felder.contains("datenschutzAkzeptiert")
                    ? "Datenschutz muss akzeptiert werden."
                    : "Die Anfrage ist unvollständig oder ungültig.";
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", meldung, "felder", felder));
        }
        return verarbeite(dto, bilder);
    }

    /**
     * Reiner JSON-Eingang ohne Bilder (z.B. wenn die Webseite die Bilder direkt
     * an einen anderen Endpoint hochlädt – aktuell nicht genutzt).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> empfangeFunnelAnfrageJson(
            @Valid @org.springframework.web.bind.annotation.RequestBody AnfrageFunnelRequestDto dto) {
        return verarbeite(dto, List.of());
    }

    private ResponseEntity<Map<String, Object>> verarbeite(AnfrageFunnelRequestDto dto, List<MultipartFile> bilder) {
        try {
            Anfrage anfrage = anfrageFunnelService.verarbeiteFunnelAnfrage(dto, bilder);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "anfrageId", anfrage.getId(),
                    "message", "Anfrage erfolgreich angelegt."
            ));
        } catch (FunnelAnfrageAbgelehntException e) {
            // Spam-Check läuft seit Refactor asynchron — dieser Branch wird aktuell
            // nicht mehr ausgelöst. Er bleibt bewusst erhalten, da die Funnel-Webseite
            // den Code SPAM_ABGELEHNT auswertet und ein künftiger synchroner Check
            // (z. B. regelbasiert ohne KI) ihn wieder nutzen könnte.
            log.info("Funnel-Anfrage als Spam abgelehnt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("success", false, "code", "SPAM_ABGELEHNT",
                            "message", e.getMessage() != null ? e.getMessage()
                                    : "Anfrage wirkt nicht ernst gemeint und konnte nicht gesendet werden."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Funnel-Anfrage abgelehnt: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler bei Funnel-Anfrage", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Interner Fehler."));
        }
    }
}
