package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.service.AbwesenheitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller für Abwesenheiten.
 * Ermöglicht direkte Buchung von Urlaub, Krankheit, Fortbildung ohne
 * Urlaubsantrag.
 */
@RestController
@RequestMapping("/api/abwesenheit")
@RequiredArgsConstructor
public class AbwesenheitController {

    private final AbwesenheitService abwesenheitService;

    /**
     * Bucht eine neue Abwesenheit für einen Mitarbeiter.
     * 
     * Body: {
     * "mitarbeiterId": 1,
     * "datum": "2025-12-24",
     * "typ": "URLAUB",
     * "halberTag": false
     * }
     */
    @PostMapping
    public ResponseEntity<?> bucheAbwesenheit(@RequestBody Map<String, Object> body) {
        try {
            Long mitarbeiterId = ((Number) body.get("mitarbeiterId")).longValue();
            LocalDate datum = LocalDate.parse((String) body.get("datum"));
            AbwesenheitsTyp typ = AbwesenheitsTyp.valueOf((String) body.get("typ"));
            boolean halberTag = body.get("halberTag") != null && (Boolean) body.get("halberTag");

            Abwesenheit abwesenheit = abwesenheitService.bucheAbwesenheit(mitarbeiterId, datum, typ, halberTag);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", abwesenheit.getId(),
                    "datum", abwesenheit.getDatum().toString(),
                    "typ", abwesenheit.getTyp().name(),
                    "stunden", abwesenheit.getStunden(),
                    "notiz", abwesenheit.getNotiz() != null ? abwesenheit.getNotiz() : ""));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fehler beim Buchen: " + e.getMessage()));
        }
    }

    /**
     * Gibt alle Abwesenheiten eines Mitarbeiters zurück.
     */
    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    public ResponseEntity<List<Map<String, Object>>> getAbwesenheitenByMitarbeiter(
            @PathVariable Long mitarbeiterId,
            @RequestParam(required = false) String von,
            @RequestParam(required = false) String bis) {

        List<Abwesenheit> abwesenheiten;
        if (von != null && bis != null) {
            abwesenheiten = abwesenheitService.getAbwesenheitenByMitarbeiterAndZeitraum(
                    mitarbeiterId, LocalDate.parse(von), LocalDate.parse(bis));
        } else {
            abwesenheiten = abwesenheitService.getAbwesenheitenByMitarbeiter(mitarbeiterId);
        }

        List<Map<String, Object>> result = abwesenheiten.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "datum", a.getDatum().toString(),
                        "typ", a.getTyp().name(),
                        "stunden", a.getStunden(),
                        "notiz", a.getNotiz() != null ? a.getNotiz() : "",
                        "urlaubsantragId", a.getUrlaubsantrag() != null ? a.getUrlaubsantrag().getId() : null))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Gibt alle Abwesenheiten aller Mitarbeiter für einen Zeitraum zurück
     * (Team-Kalender).
     * Jeder sieht, wer wann abwesend ist.
     */
    @GetMapping("/team")
    public ResponseEntity<List<Map<String, Object>>> getTeamAbwesenheiten(
            @RequestParam String von,
            @RequestParam String bis) {

        LocalDate vonDate = LocalDate.parse(von);
        LocalDate bisDate = LocalDate.parse(bis);

        List<Abwesenheit> abwesenheiten = abwesenheitService.getAllAbwesenheitenForZeitraum(vonDate, bisDate);

        List<Map<String, Object>> result = abwesenheiten.stream()
                .map(a -> {
                    var mitarbeiter = a.getMitarbeiter();
                    String name = mitarbeiter != null
                            ? mitarbeiter.getVorname() + " " + mitarbeiter.getNachname()
                            : "Unbekannt";
                    return Map.<String, Object>of(
                            "id", a.getId(),
                            "datum", a.getDatum().toString(),
                            "typ", a.getTyp().name(),
                            "stunden", a.getStunden(),
                            "mitarbeiterId", mitarbeiter != null ? mitarbeiter.getId() : 0,
                            "mitarbeiterName", name);
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Löscht eine Abwesenheit.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> loescheAbwesenheit(@PathVariable Long id) {
        try {
            abwesenheitService.loescheAbwesenheit(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
