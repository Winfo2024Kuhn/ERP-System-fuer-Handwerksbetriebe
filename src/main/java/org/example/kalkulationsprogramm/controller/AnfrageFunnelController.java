package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Public S2S-Endpoint für den Anfrage-Funnel der Marketing-Webseite.
 * Erreichbar nur über Cloudflare-Tunnel + Access Service Token. Lokal/im LAN
 * sollte der Spring-Port nicht direkt exponiert werden.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/anfrage")
@RequiredArgsConstructor
public class AnfrageFunnelController {

    private final AnfrageFunnelService anfrageFunnelService;
    private final ObjectMapper objectMapper;

    /**
     * Multipart-Eingang: JSON-Payload als Part {@code anfrage}, Bilder als Parts
     * {@code bilder} (optional, mehrfach).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> empfangeFunnelAnfrage(
            @RequestPart("anfrage") String anfrageJson,
            @RequestPart(value = "bilder", required = false) List<MultipartFile> bilder) {
        AnfrageFunnelRequestDto dto;
        try {
            dto = objectMapper.readValue(anfrageJson, AnfrageFunnelRequestDto.class);
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Anfrage-Payload ist kein gültiges JSON."));
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
