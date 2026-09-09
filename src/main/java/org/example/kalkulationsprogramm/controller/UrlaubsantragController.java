package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.service.UrlaubsantragService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UrlaubsantragController {

    private final UrlaubsantragService service;

    @PostMapping("/api/urlaub/antraege")
    public ResponseEntity<?> createAntrag(@RequestBody Map<String, Object> body) {
        try {
            Long mitarbeiterId = ((Number) body.get("mitarbeiterId")).longValue();
            LocalDate von = LocalDate.parse((String) body.get("von"));
            LocalDate bis = LocalDate.parse((String) body.get("bis"));
            String bemerkung = (String) body.get("bemerkung");
            String typStr = (String) body.get("typ");
            Urlaubsantrag.Typ typ = typStr != null ? Urlaubsantrag.Typ.valueOf(typStr) : Urlaubsantrag.Typ.URLAUB;

            return ResponseEntity.ok(service.createAntrag(mitarbeiterId, von, bis, bemerkung, typ));
        } catch (IllegalStateException e) {
            // Überlappungsfehler
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/urlaub/antraege")
    public ResponseEntity<List<Urlaubsantrag>> getAntraege(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long mitarbeiterId,
            @RequestParam(required = false) Integer jahr) {

        // Mitarbeiter + Jahr Filter
        if (mitarbeiterId != null && jahr != null) {
            return ResponseEntity.ok(service.getAntraegeByMitarbeiterAndYear(mitarbeiterId, jahr));
        }

        // Mitarbeiter + Status Filter
        if (mitarbeiterId != null && status != null) {
            try {
                Urlaubsantrag.Status antragStatus = Urlaubsantrag.Status.valueOf(status);
                return ResponseEntity.ok(service.getAntraegeByMitarbeiterAndStatus(mitarbeiterId, antragStatus));
            } catch (IllegalArgumentException e) {
                // Ungültiger Status, alle zurückgeben
            }
        }

        // Nur Mitarbeiter
        if (mitarbeiterId != null) {
            return ResponseEntity.ok(service.getAntraegeByMitarbeiter(mitarbeiterId));
        }

        // Nur Status (für alle gültigen Status-Werte)
        if (status != null && !status.isBlank()) {
            try {
                Urlaubsantrag.Status antragStatus = Urlaubsantrag.Status.valueOf(status);
                return ResponseEntity.ok(service.getAntraegeByStatus(antragStatus));
            } catch (IllegalArgumentException e) {
                // Ungültiger Status, offene zurückgeben
            }
        }

        // Default: offene Anträge
        return ResponseEntity.ok(service.getOffeneAntraege());
    }

    @PutMapping("/api/urlaub/antraege/{id}/approve")
    public ResponseEntity<Urlaubsantrag> approveAntrag(@PathVariable Long id) {
        return ResponseEntity.ok(service.approveAntrag(id));
    }

    @PutMapping("/api/urlaub/antraege/{id}/reject")
    public ResponseEntity<Urlaubsantrag> rejectAntrag(@PathVariable Long id) {
        return ResponseEntity.ok(service.rejectAntrag(id));
    }

    @PutMapping("/api/urlaub/antraege/{id}/storno")
    public ResponseEntity<Urlaubsantrag> stornoAntrag(@PathVariable Long id) {
        return ResponseEntity.ok(service.stornoAntrag(id));
    }

    /**
     * Gibt die verbleibenden Urlaubstage eines Mitarbeiters für ein Jahr zurück.
     */
    @GetMapping("/api/urlaub/resturlaub")
    public ResponseEntity<?> getResturlaub(
            @RequestParam Long mitarbeiterId,
            @RequestParam(required = false) Integer jahr) {
        int targetJahr = (jahr != null) ? jahr : java.time.LocalDate.now().getYear();
        try {
            int verbleibend = service.getResturlaub(mitarbeiterId, targetJahr);
            return ResponseEntity.ok(Map.of("verbleibend", verbleibend, "jahr", targetJahr));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Liefert einen Hinweis, wenn der angefragte Urlaubszeitraum in eine
     * laufende Krankmeldung fällt. Reine Warnung fürs Büro, keine Sperre —
     * der Antrag selbst (POST /api/urlaub/antraege, auch von der Handy-App
     * genutzt) bleibt davon unberührt.
     *
     * <p><b>Bewusst NICHT unter {@code /api/urlaub/**}:</b> Dieses Präfix
     * steht auf der {@code permitAll}-Kette der Mobile-App
     * ({@link org.example.kalkulationsprogramm.config.SecurityConfig#ZEITERFASSUNG_PATHS}).
     * Die Antwort verrät Existenz und Beginndatum einer Langzeitkrankmeldung —
     * Gesundheitsdaten nach Art. 9 DSGVO — und muss deshalb hinter dem Login
     * liegen. {@code /api/langzeitkrankmeldungen/**} ist bereits
     * {@code authenticated()} (siehe {@code apiFilterChain}), ohne dass diese
     * SecurityConfig dafür angefasst werden musste (Abschnitt 4 Nachbesserung,
     * Befund 1; abgesichert durch {@code UrlaubsHinweiseSicherheitTest} im
     * {@code config}-Testpackage).
     */
    @GetMapping("/api/langzeitkrankmeldungen/urlaubs-hinweise")
    public ResponseEntity<Map<String, List<String>>> getHinweise(
            @RequestParam Long mitarbeiterId,
            @RequestParam LocalDate von,
            @RequestParam LocalDate bis) {
        return ResponseEntity.ok(Map.of("warnungen", service.pruefeHinweise(mitarbeiterId, von, bis)));
    }

    /**
     * Gibt alle verfügbaren Abwesenheitstypen zurück.
     */
    @GetMapping("/api/urlaub/typen")
    public ResponseEntity<List<Map<String, String>>> getAbwesenheitsTypen() {
        List<Map<String, String>> typen = List.of(
                Map.of("value", "URLAUB", "label", "Urlaub"),
                Map.of("value", "KRANKHEIT", "label", "Krankheit"),
                Map.of("value", "FORTBILDUNG", "label", "Fortbildung"),
                Map.of("value", "ZEITAUSGLEICH", "label", "Zeitausgleich"));
        return ResponseEntity.ok(typen);
    }
}
