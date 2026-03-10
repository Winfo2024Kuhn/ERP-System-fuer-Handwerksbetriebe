package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.ZeitkontoKorrektur;
import org.example.kalkulationsprogramm.service.ZeitkontoKorrekturService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller für Zeitkonto-Korrekturen.
 * Ermöglicht manuelle Ausgleichsbuchungen ohne Projektzuordnung.
 * 
 * GoBD-konform: Alle Änderungen werden unveränderlich protokolliert.
 */
@RestController
@RequestMapping("/api/zeitkonto/korrekturen")
@RequiredArgsConstructor
public class ZeitkontoKorrekturController {

    private final ZeitkontoKorrekturService korrekturService;

    /**
     * Erstellt eine neue Zeitkonto-Korrektur.
     * 
     * Body: {
     * "mitarbeiterId": 1,
     * "stunden": 8.5, // Positiv = Gutschrift, Negativ = Abzug
     * "datum": "2025-12-21",
     * "grund": "Überstundenausgleich Q4 2025",
     * "erstelltVonId": 1,
     * "typ": "STUNDEN" // oder "URLAUB"
     * }
     */
    @PostMapping
    public ResponseEntity<?> erstelleKorrektur(@RequestBody Map<String, Object> body) {
        try {
            Long mitarbeiterId = getLong(body, "mitarbeiterId");
            BigDecimal stunden = getBigDecimal(body, "stunden");
            LocalDate datum = LocalDate.parse((String) body.get("datum"));
            String grund = (String) body.get("grund");
            Long erstelltVonId = getLong(body, "erstelltVonId");

            // Typ parsen (optional, default STUNDEN)
            String typStr = (String) body.get("typ");
            org.example.kalkulationsprogramm.domain.KorrekturTyp typ = typStr != null
                    ? org.example.kalkulationsprogramm.domain.KorrekturTyp.valueOf(typStr)
                    : org.example.kalkulationsprogramm.domain.KorrekturTyp.STUNDEN;

            ZeitkontoKorrektur korrektur = korrekturService.erstelleKorrektur(
                    mitarbeiterId, stunden, datum, grund, erstelltVonId, ErfassungsQuelle.DESKTOP, typ);

            return ResponseEntity.status(HttpStatus.CREATED).body(korrekturToMap(korrektur));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fehler beim Erstellen: " + e.getMessage()));
        }
    }

    /**
     * Aktualisiert eine bestehende Korrektur (GoBD-konform mit Audit).
     * 
     * Body: {
     * "stunden": 10.0,
     * "grund": "Korrigierter Wert",
     * "bearbeiterId": 1,
     * "aenderungsgrund": "Tippfehler korrigiert"
     * }
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> aendereKorrektur(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal stunden = body.get("stunden") != null ? getBigDecimal(body, "stunden") : null;
            String grund = (String) body.get("grund");
            Long bearbeiterId = getLong(body, "bearbeiterId");
            String aenderungsgrund = (String) body.get("aenderungsgrund");

            ZeitkontoKorrektur korrektur = korrekturService.aendereKorrektur(
                    id, stunden, grund, bearbeiterId, aenderungsgrund, ErfassungsQuelle.DESKTOP);

            return ResponseEntity.ok(korrekturToMap(korrektur));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Storniert eine Korrektur (GoBD: keine physische Löschung!).
     * 
     * Body: {
     * "bearbeiterId": 1,
     * "stornierungsgrund": "Fehlerhafte Buchung"
     * }
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> storniereKorrektur(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Long bearbeiterId = getLong(body, "bearbeiterId");
            String stornierungsgrund = (String) body.get("stornierungsgrund");

            korrekturService.storniereKorrektur(id, bearbeiterId, stornierungsgrund, ErfassungsQuelle.DESKTOP);

            return ResponseEntity.ok(Map.of("success", true, "message", "Korrektur wurde storniert"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gibt alle aktiven Korrekturen eines Mitarbeiters zurück.
     */
    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    public ResponseEntity<List<Map<String, Object>>> getKorrekturenByMitarbeiter(
            @PathVariable Long mitarbeiterId,
            @RequestParam(defaultValue = "false") boolean alleAnzeigen) {

        List<ZeitkontoKorrektur> korrekturen = alleAnzeigen
                ? korrekturService.getAlleKorrekturenByMitarbeiter(mitarbeiterId)
                : korrekturService.getAktiveKorrekturenByMitarbeiter(mitarbeiterId);

        List<Map<String, Object>> result = korrekturen.stream()
                .map(this::korrekturToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Gibt die Audit-Historie einer Korrektur zurück (für GoBD-Prüfung).
     */
    @GetMapping("/{id}/historie")
    public ResponseEntity<List<Map<String, Object>>> getAuditHistorie(@PathVariable Long id) {
        return ResponseEntity.ok(korrekturService.getAuditHistorie(id));
    }

    /**
     * Gibt die Summe aller aktiven Korrekturen eines Mitarbeiters für ein Jahr
     * zurück.
     */
    @GetMapping("/mitarbeiter/{mitarbeiterId}/summe")
    public ResponseEntity<Map<String, Object>> getSumme(
            @PathVariable Long mitarbeiterId,
            @RequestParam(defaultValue = "0") int jahr) {

        if (jahr == 0) {
            jahr = LocalDate.now().getYear();
        }

        BigDecimal summe = korrekturService.summiereAktiveKorrekturen(mitarbeiterId, jahr);

        return ResponseEntity.ok(Map.of(
                "mitarbeiterId", mitarbeiterId,
                "jahr", jahr,
                "summeStunden", summe));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> korrekturToMap(ZeitkontoKorrektur k) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", k.getId());
        map.put("mitarbeiterId", k.getMitarbeiter().getId());
        map.put("mitarbeiterName", k.getMitarbeiter().getVorname() + " " + k.getMitarbeiter().getNachname());
        map.put("datum", k.getDatum().toString());
        map.put("stunden", k.getStunden());
        map.put("grund", k.getGrund());
        map.put("version", k.getVersion());
        map.put("typ", k.getTyp().name());
        map.put("erstelltAm", k.getErstelltAm().toString());
        map.put("erstelltVon", k.getErstelltVon() != null
                ? k.getErstelltVon().getVorname() + " " + k.getErstelltVon().getNachname()
                : null);
        map.put("storniert", k.getStorniert());
        if (k.getStorniert()) {
            map.put("storniertAm", k.getStorniertAm() != null ? k.getStorniertAm().toString() : null);
            map.put("storniertVon", k.getStorniertVon() != null
                    ? k.getStorniertVon().getVorname() + " " + k.getStorniertVon().getNachname()
                    : null);
            map.put("stornierungsgrund", k.getStornierungsgrund());
        }
        return map;
    }

    private Long getLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " ist ein Pflichtfeld");
        }
        return ((Number) value).longValue();
    }

    private BigDecimal getBigDecimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " ist ein Pflichtfeld");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
