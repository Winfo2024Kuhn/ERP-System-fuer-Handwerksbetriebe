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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    /** Header, mit dem die Webseite ihren Fehlercode mitschickt (gleicher Code in beiden Logs). */
    static final String ANFRAGE_CODE_HEADER = "X-Anfrage-Code";
    private static final Pattern ANFRAGE_CODE = Pattern.compile("[a-z0-9]{1,12}");

    /** Feldnamen so, wie der Kunde sie im Formular sieht. */
    private static final Map<String, String> FELD_LABELS = new LinkedHashMap<>();
    static {
        FELD_LABELS.put("serviceTyp", "Leistung");
        FELD_LABELS.put("projektarten", "Projektart");
        FELD_LABELS.put("nachricht", "Nachricht");
        FELD_LABELS.put("vorname", "Vorname");
        FELD_LABELS.put("nachname", "Nachname");
        FELD_LABELS.put("email", "E-Mail");
        FELD_LABELS.put("telefon", "Telefon");
        FELD_LABELS.put("projektAnschrift", "Projektadresse");
        FELD_LABELS.put("rechnungsAnschrift", "Rechnungsadresse");
    }

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
            @RequestPart(value = "bilder", required = false) List<MultipartFile> bilder,
            @RequestHeader(value = ANFRAGE_CODE_HEADER, required = false) String anfrageCode) {
        String code = codeFuerLog(anfrageCode);
        AnfrageFunnelRequestDto dto;
        try {
            dto = objectMapper.readValue(anfrageJson, AnfrageFunnelRequestDto.class);
        } catch (JacksonException e) {
            log.warn("Funnel-Anfrage [{}] abgelehnt: Payload ist kein gültiges JSON ({})", code, e.getOriginalMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Anfrage-Payload ist kein gültiges JSON."));
        }
        if (dto == null) {
            log.warn("Funnel-Anfrage [{}] abgelehnt: Payload fehlt", code);
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
            log.warn("Funnel-Anfrage [{}] abgelehnt, ungültige Felder: {}", code, felder);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", meldungFuerFelder(felder), "felder", felder));
        }
        return verarbeite(dto, bilder, code);
    }

    /**
     * Text für den Kunden (die Webseite zeigt ihn 1:1 an): nennt die Felder so,
     * wie sie im Formular heißen, statt nur "ungültig" zu sagen.
     */
    static String meldungFuerFelder(List<String> felder) {
        if (felder.contains("datenschutzAkzeptiert")) {
            return "Bitte bestätigen Sie die Datenschutzerklärung.";
        }
        // Reihenfolge wie im Formular, nicht alphabetisch
        Set<String> betroffen = felder.stream()
                .map(f -> f.replaceAll("\\[.*$", ""))
                .collect(java.util.stream.Collectors.toSet());
        List<String> labels = FELD_LABELS.entrySet().stream()
                .filter(e -> betroffen.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (labels.isEmpty()) {
            return "Einige Angaben fehlen oder passen nicht. Bitte prüfen Sie das Formular.";
        }
        return "Bitte prüfen Sie folgende Angaben: " + String.join(", ", labels) + ".";
    }

    /** Nur kurze Codes aus Kleinbuchstaben/Ziffern ins Log, alles andere wird zu "-". */
    static String codeFuerLog(String roh) {
        return roh != null && ANFRAGE_CODE.matcher(roh).matches() ? roh : "-";
    }

    private static String bilderKurz(List<MultipartFile> bilder) {
        if (bilder == null || bilder.isEmpty()) {
            return "0";
        }
        long kb = bilder.stream().mapToLong(MultipartFile::getSize).sum() / 1024;
        return bilder.size() + " (" + kb + " KB)";
    }

    /**
     * Reiner JSON-Eingang ohne Bilder (z.B. wenn die Webseite die Bilder direkt
     * an einen anderen Endpoint hochlädt – aktuell nicht genutzt).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> empfangeFunnelAnfrageJson(
            @Valid @org.springframework.web.bind.annotation.RequestBody AnfrageFunnelRequestDto dto,
            @RequestHeader(value = ANFRAGE_CODE_HEADER, required = false) String anfrageCode) {
        return verarbeite(dto, List.of(), codeFuerLog(anfrageCode));
    }

    private ResponseEntity<Map<String, Object>> verarbeite(AnfrageFunnelRequestDto dto, List<MultipartFile> bilder, String code) {
        long start = System.currentTimeMillis();
        try {
            Anfrage anfrage = anfrageFunnelService.verarbeiteFunnelAnfrage(dto, bilder);
            log.info("Funnel-Anfrage [{}] angelegt: anfrageId={}, bilder={}, {} ms",
                    code, anfrage.getId(), bilderKurz(bilder), System.currentTimeMillis() - start);
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
            log.info("Funnel-Anfrage [{}] als Spam abgelehnt: {}", code, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("success", false, "code", "SPAM_ABGELEHNT",
                            "message", e.getMessage() != null ? e.getMessage()
                                    : "Anfrage wirkt nicht ernst gemeint und konnte nicht gesendet werden."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Funnel-Anfrage [{}] abgelehnt: {}", code, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler bei Funnel-Anfrage [{}] (bilder={}, {} ms)",
                    code, bilderKurz(bilder), System.currentTimeMillis() - start, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Interner Fehler."));
        }
    }
}
