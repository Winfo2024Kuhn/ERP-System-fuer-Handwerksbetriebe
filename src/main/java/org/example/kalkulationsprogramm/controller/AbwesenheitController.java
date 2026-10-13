package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.service.AbwesenheitService;
import org.example.kalkulationsprogramm.service.GesundheitsdatenSichtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    private final GesundheitsdatenSichtService gesundheitsdatenSichtService;

    /** Neutraler Grund im Team-Kalender, wenn der Betrachter „Krankheit" nicht sehen darf. */
    static final String TYP_ABWESEND = "ABWESEND";

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
        Long mitarbeiterId;
        LocalDate datum;
        AbwesenheitsTyp typ;
        boolean halberTag;
        BigDecimal stunden;
        try {
            mitarbeiterId = ((Number) body.get("mitarbeiterId")).longValue();
            datum = LocalDate.parse((String) body.get("datum"));
            typ = AbwesenheitsTyp.valueOf((String) body.get("typ"));
            halberTag = body.get("halberTag") != null && (Boolean) body.get("halberTag");
            stunden = body.get("stunden") != null ? new BigDecimal(body.get("stunden").toString()) : null;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Angaben zur Abwesenheit sind unvollständig oder ungültig.");
        }

        // Fehler als ApiError (message): 400 bei Regelverstoß, 409 „Monat festgeschrieben"
        // wirft der Service selbst als ResponseStatusException und läuft hier durch
        // (Issue #269, Punkt 8) – vorher landete er im Sammel-catch als 500.
        Abwesenheit abwesenheit;
        try {
            abwesenheit = abwesenheitService.bucheAbwesenheit(mitarbeiterId, datum, typ, halberTag, stunden);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", abwesenheit.getId(),
                "datum", abwesenheit.getDatum().toString(),
                "typ", abwesenheit.getTyp().name(),
                "stunden", abwesenheit.getStunden(),
                "notiz", abwesenheit.getNotiz() != null ? abwesenheit.getNotiz() : ""));
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
     * (Team-Kalender). Jeder sieht, wer wann abwesend ist – den Grund
     * „Krankheit" aber nur Admins, die Geschäftsführung und der Betroffene
     * selbst; alle anderen bekommen {@code typ = "ABWESEND"} (Issue #268).
     */
    @GetMapping("/team")
    public ResponseEntity<List<Map<String, Object>>> getTeamAbwesenheiten(
            @RequestParam String von,
            @RequestParam String bis,
            Authentication authentication) {

        LocalDate vonDate = LocalDate.parse(von);
        LocalDate bisDate = LocalDate.parse(bis);
        GesundheitsdatenSichtService.Sicht sicht = gesundheitsdatenSichtService.sichtFuer(authentication);

        List<Abwesenheit> abwesenheiten = abwesenheitService.getAllAbwesenheitenForZeitraum(vonDate, bisDate);

        List<Map<String, Object>> result = abwesenheiten.stream()
                .map(a -> {
                    var mitarbeiter = a.getMitarbeiter();
                    Long mitarbeiterId = mitarbeiter != null ? mitarbeiter.getId() : null;
                    String name = mitarbeiter != null
                            ? mitarbeiter.getVorname() + " " + mitarbeiter.getNachname()
                            : "Unbekannt";
                    return Map.<String, Object>of(
                            "id", a.getId(),
                            "datum", a.getDatum().toString(),
                            "typ", sichtbarerTyp(a, sicht, mitarbeiterId),
                            "stunden", a.getStunden(),
                            "mitarbeiterId", mitarbeiterId != null ? mitarbeiterId : 0,
                            "mitarbeiterName", name);
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    static String sichtbarerTyp(Abwesenheit a, GesundheitsdatenSichtService.Sicht sicht, Long mitarbeiterId) {
        if (a.getTyp() == AbwesenheitsTyp.KRANKHEIT && !sicht.darfGrundSehen(mitarbeiterId)) {
            return TYP_ABWESEND;
        }
        return a.getTyp().name();
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
