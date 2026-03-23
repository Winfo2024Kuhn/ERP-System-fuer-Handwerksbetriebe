package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.service.ZeiterfassungApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST API Controller für die Zeiterfassungs-PWA.
 * Liefert gefilterte Daten und verarbeitet Zeitbuchungen.
 */
@RestController
@RequestMapping("/api/zeiterfassung")
@RequiredArgsConstructor
public class ZeiterfassungApiController {

    private final ZeiterfassungApiService service;

    // ==================== Daten abrufen ====================

    /**
     * Gibt nur offene Projekte zurück (abschlussdatum = null)
     */
    @GetMapping("/projekte")
    public ResponseEntity<List<Map<String, Object>>> getOpenProjekte(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getOpenProjekte(limit, search));
    }

    /**
     * Gibt alle Kategorien mit vollem Pfad zurück (rekursive Ordnerstruktur)
     */
    @GetMapping("/kategorien")
    public ResponseEntity<List<Map<String, Object>>> getKategorienMitPfad() {
        return ResponseEntity.ok(service.getKategorienMitPfad());
    }

    /**
     * Gibt nur die dem Projekt zugeordneten Kategorien zurück (via
     * ProjektProduktkategorie)
     */
    @GetMapping("/kategorien/{projektId}")
    public ResponseEntity<List<Map<String, Object>>> getKategorienByProjekt(@PathVariable Long projektId) {
        return ResponseEntity.ok(service.getKategorienByProjektId(projektId));
    }

    /**
     * Gibt Arbeitsgänge gefiltert nach Abteilung des Mitarbeiters zurück
     */
    @GetMapping("/arbeitsgaenge/{token}")
    public ResponseEntity<List<ArbeitsgangResponseDto>> getArbeitsgaengeForMitarbeiter(@PathVariable String token) {
        return service.getArbeitsgaengeByMitarbeiterToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Gibt alle aktiven Lieferanten zurück
     */
    @GetMapping("/lieferanten")
    public ResponseEntity<List<Map<String, Object>>> getLieferanten(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getLieferanten(limit, search));
    }

    // ==================== Zeiterfassung Start/Stop ====================

    /**
     * Startet eine neue Zeitbuchung
     * Body: { "token": "xxx", "projektId": 1, "arbeitsgangId": 1,
     * "produktkategorieId": 1 (optional) }
     */
    @PostMapping("/start")
    public ResponseEntity<?> startZeiterfassung(@RequestBody Map<String, Object> body) {
        try {
            String token = (String) body.get("token");
            Long projektId = ((Number) body.get("projektId")).longValue();
            Long arbeitsgangId = ((Number) body.get("arbeitsgangId")).longValue();
            Long produktkategorieId = body.get("produktkategorieId") != null
                    ? ((Number) body.get("produktkategorieId")).longValue()
                    : null;

            // Optional: Original-Zeitstempel für Offline-Sync
            LocalDateTime originalZeit = parseOptionalDateTime(body.get("originalZeit"));

            // Optional: Idempotency-Key für Offline-Sync (UUID vom Client)
            String idempotencyKey = body.get("idempotencyKey") != null
                    ? body.get("idempotencyKey").toString()
                    : null;

            Map<String, Object> result = service.startZeiterfassung(token, projektId, arbeitsgangId,
                    produktkategorieId, originalZeit, idempotencyKey);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Stoppt die aktive Zeitbuchung
     * Body: { "token": "xxx" }
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stopZeiterfassung(@RequestBody Map<String, Object> body) {
        try {
            String token = (String) body.get("token");

            // Optional: Original-Zeitstempel für Offline-Sync
            LocalDateTime originalZeit = parseOptionalDateTime(body.get("originalZeit"));

            String idempotencyKey = body.get("idempotencyKey") != null
                    ? body.get("idempotencyKey").toString()
                    : null;

            Map<String, Object> result = service.stopZeiterfassung(token, originalZeit, idempotencyKey);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Startet eine Pause (stoppt aktive Buchung, startet Pause-Buchung)
     * Body: { "token": "xxx" }
     */
    @PostMapping("/pause")
    public ResponseEntity<?> startPause(@RequestBody Map<String, Object> body) {
        try {
            String token = (String) body.get("token");

            // Optional: Original-Zeitstempel für Offline-Sync
            LocalDateTime originalZeit = parseOptionalDateTime(body.get("originalZeit"));

            String idempotencyKey = body.get("idempotencyKey") != null
                    ? body.get("idempotencyKey").toString()
                    : null;

            Map<String, Object> result = service.startPause(token, originalZeit, idempotencyKey);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Hilfsmethode: Parst einen optionalen DateTime-String (ISO-8601).
     * Unterstützt sowohl "2026-02-12T07:00:00" als auch "2026-02-12T07:00:00.000Z"
     * Format.
     * Gibt null zurück wenn der Wert nicht vorhanden oder nicht parsbar ist.
     */
    private LocalDateTime parseOptionalDateTime(Object value) {
        if (value == null)
            return null;
        try {
            String str = value.toString().trim();
            // Entferne Zeitzonen-Info (Z oder +01:00) – Server arbeitet immer in lokaler
            // Zeit
            if (str.endsWith("Z")) {
                // UTC → LocalDateTime (wir vertrauen der Client-Zeit)
                return java.time.Instant.parse(str)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            } else if (str.contains("+") && str.lastIndexOf('+') > 10) {
                // Offset wie +01:00
                return java.time.OffsetDateTime.parse(str)
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            }
            return LocalDateTime.parse(str);
        } catch (Exception e) {
            // Nicht parsbar → als null behandeln (Fallback auf now())
            return null;
        }
    }

    /**
     * Gibt die aktive Buchung für einen Mitarbeiter zurück
     */
    @GetMapping("/aktiv/{token}")
    public ResponseEntity<?> getAktiveBuchung(@PathVariable String token) {
        return service.getAktiveBuchung(token)
                .map(buchung -> ResponseEntity.ok((Object) buchung))
                .orElse(ResponseEntity.ok(Map.of("aktiv", false)));
    }

    /**
     * Gibt die heute gearbeiteten Stunden für einen Mitarbeiter zurück
     */
    @GetMapping("/heute/{token}")
    public ResponseEntity<Map<String, Object>> getHeuteGearbeitet(@PathVariable String token) {
        return ResponseEntity.ok(service.getHeuteGearbeitet(token));
    }

    /**
     * Gibt alle Bilder (DokumentGruppe=BILDER) für ein Projekt zurück
     */
    @GetMapping("/projekte/{projektId}/bilder")
    public ResponseEntity<List<Map<String, Object>>> getProjektBilder(@PathVariable Long projektId) {
        return ResponseEntity.ok(service.getProjektBilder(projektId));
    }

    /**
     * Gibt das erlaubte Buchungszeitfenster für einen Mitarbeiter zurück.
     * Wird von der PWA genutzt, um Buchungen clientseitig automatisch zu beenden.
     */
    @GetMapping("/buchungszeitfenster/{token}")
    public ResponseEntity<?> getBuchungszeitfenster(@PathVariable String token) {
        try {
            return ResponseEntity.ok(service.getBuchungszeitfenster(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gibt die Saldenauswertung für einen Mitarbeiter zurück:
     * - Urlaubstage (genommen/verbleibend)
     * - Monatsstunden (Soll/Ist)
     * - Gesamtsaldo
     * 
     * GESAMTSALDO-BERECHNUNG:
     * - Mobile App: gesamtBisHeute=true → Saldo wird IMMER bis zum heutigen Tag
     * berechnet
     * - PC Frontend: gesamtBisHeute nicht gesetzt → Saldo wird bis Ende des
     * ausgewählten Jahres berechnet
     * (bei aktuellem Jahr bis heute, bei vergangenen Jahren bis 31.12)
     */
    @GetMapping("/saldo/{token}")
    public ResponseEntity<Map<String, Object>> getSaldo(
            @PathVariable String token,
            @RequestParam(required = false) Integer jahr,
            @RequestParam(required = false) Integer monat,
            @RequestParam(required = false) Boolean gesamtBisHeute) {
        return ResponseEntity.ok(service.getSaldo(token, jahr, monat, gesamtBisHeute));
    }

    /**
     * Gibt alle Buchungen für einen Mitarbeiter an einem bestimmten Datum zurück.
     * Falls kein Datum angegeben, wird heute verwendet.
     */
    @GetMapping("/buchungen/{token}")
    public ResponseEntity<List<Map<String, Object>>> getBuchungen(
            @PathVariable String token,
            @RequestParam(required = false) String datum) {
        java.time.LocalDate date = datum != null
                ? java.time.LocalDate.parse(datum)
                : java.time.LocalDate.now();
        return ResponseEntity.ok(service.getBuchungenByDatum(token, date));
    }

    /**
     * Gibt alle Feiertage für ein Jahr zurück (Bayern).
     */
    @GetMapping("/feiertage")
    public ResponseEntity<List<Map<String, Object>>> getFeiertage(@RequestParam int jahr) {
        return ResponseEntity.ok(service.getFeiertage(jahr));
    }

    /**
     * Gibt eine Urlaubsverfall-Warnung zurück, falls Resturlaub verfällt.
     * Warnung wird 2 Monate vorher angezeigt (ab 1. Dezember).
     * 
     * Response: {
     * "resturlaubTage": 5,
     * "verfallsDatum": "2026-02-01",
     * "verfallsJahr": 2026,
     * "tageVerbleibend": 42,
     * "dringend": false
     * }
     * oder leeres Objekt {} wenn keine Warnung nötig.
     */
    @GetMapping("/urlaubsverfall/{token}")
    public ResponseEntity<Map<String, Object>> getUrlaubsverfallWarnung(@PathVariable String token) {
        return ResponseEntity.ok(service.getUrlaubsverfallWarnung(token));
    }
}
