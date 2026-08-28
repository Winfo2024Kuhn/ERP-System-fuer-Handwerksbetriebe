package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiAnfrage;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiEntwurf;
import org.example.kalkulationsprogramm.service.BeitragKiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Erzeugt Textvorschlaege fuer Website-Beitraege.
 *
 * <p>Liegt unter {@code /api/beitraege/**} und ist damit ohne weiteres Zutun
 * auf Rolle ADMIN beschraenkt ({@code SecurityConfig.java:224}).
 */
@Slf4j
@RestController
@RequestMapping("/api/beitraege/ki")
@RequiredArgsConstructor
public class BeitragKiController {

    private final BeitragKiService beitragKiService;

    @PostMapping(value = "/entwurf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BeitragKiEntwurf> entwurf(
            @Valid @RequestPart("anfrage") BeitragKiAnfrage anfrage,
            @RequestPart(value = "bilder", required = false) List<MultipartFile> bilder) {
        return ResponseEntity.ok(beitragKiService.erzeugeEntwurf(anfrage, bilder));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleUngueltig(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleKiFehler(IllegalStateException ex) {
        log.warn("[BeitragKi] Vorschlag fehlgeschlagen: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "message", "Die KI konnte gerade keinen Vorschlag erstellen."));
    }
}
