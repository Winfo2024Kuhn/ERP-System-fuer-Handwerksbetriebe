package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungAnlegenRequest;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungDto;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungPhaseRequest;
import org.example.kalkulationsprogramm.service.LangzeitkrankmeldungService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST-Controller fuer Langzeitkrankmeldungen (Desktop-App).
 *
 * <p>Langzeitkrankmeldungen werden ausschliesslich am PC gepflegt (Vorgabe
 * des Projektinhabers vom 08.09.2026) - deshalb liegt hier der komplette
 * Schreibzugriff. Die Handy-App bekommt in Task 6 genau einen lesenden
 * Endpunkt unter {@code /api/zeiterfassung/langzeitkrankmeldung/{token}};
 * unter {@code /api/zeiterfassung/**} gibt es fuer dieses Feature keine
 * schreibenden Endpunkte.
 *
 * <p>Alle Pfade liegen unter {@code /api/langzeitkrankmeldungen} und greifen
 * damit automatisch unter die Standardregel {@code .anyRequest().authenticated()}
 * der {@code apiFilterChain} ({@code config/SecurityConfig.java}, Zeile 225) -
 * keine SecurityConfig-Aenderung noetig.
 */
@RestController
@RequestMapping("/api/langzeitkrankmeldungen")
@RequiredArgsConstructor
public class LangzeitkrankmeldungController {

    private final LangzeitkrankmeldungService service;

    @GetMapping
    public ResponseEntity<List<LangzeitkrankmeldungDto>> liste(
            @RequestParam(required = false, defaultValue = "LAUFEND") LangzeitkrankmeldungStatus status) {
        List<LangzeitkrankmeldungDto> ergebnis = service.finde(status).stream()
                .map(meldung -> service.toDto(meldung, false))
                .toList();
        return ResponseEntity.ok(ergebnis);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            Langzeitkrankmeldung meldung = service.findeMitPhasen(id);
            return ResponseEntity.ok(service.toDto(meldung, true));
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> anlegen(@RequestBody LangzeitkrankmeldungAnlegenRequest request) {
        try {
            Langzeitkrankmeldung meldung = service.anlegen(request.getMitarbeiterId(), request.getBeginn(),
                    request.getLohnfortzahlungBis(), request.getNotiz());
            return ResponseEntity.status(HttpStatus.CREATED).body(service.toDto(meldung, false));
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> aendern(@PathVariable Long id,
            @RequestBody LangzeitkrankmeldungAnlegenRequest request,
            @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.aendern(id, request.getBeginn(), request.getLohnfortzahlungBis(),
                    request.getNotiz());
            return ResponseEntity.ok(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Durchreichen an RestExceptionHandler: der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/beenden")
    public ResponseEntity<?> beenden(@PathVariable Long id,
            @RequestParam LocalDate ende,
            @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.beenden(id, ende);
            return ResponseEntity.ok(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/oeffnen")
    public ResponseEntity<?> oeffnen(@PathVariable Long id, @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.wiederEroeffnen(id);
            return ResponseEntity.ok(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/abbrechen")
    public ResponseEntity<?> abbrechen(@PathVariable Long id, @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.abbrechen(id);
            return ResponseEntity.ok(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/phasen")
    public ResponseEntity<?> phaseHinzufuegen(@PathVariable Long id,
            @RequestBody LangzeitkrankmeldungPhaseRequest request,
            @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.phaseHinzufuegen(id, request.getTyp(), request.getVonDatum(),
                    request.getBisDatum(), request.getStundenProTag());
            return ResponseEntity.status(HttpStatus.CREATED).body(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/phasen/{phasenId}")
    public ResponseEntity<?> phaseAendern(@PathVariable Long id, @PathVariable Long phasenId,
            @RequestBody LangzeitkrankmeldungPhaseRequest request,
            @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            Langzeitkrankmeldung meldung = service.phaseAendern(id, phasenId, request.getTyp(),
                    request.getVonDatum(), request.getBisDatum(), request.getStundenProTag());
            return ResponseEntity.ok(service.toDto(meldung, false));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/phasen/{phasenId}")
    public ResponseEntity<?> phaseLoeschen(@PathVariable Long id, @PathVariable Long phasenId,
            @RequestParam Long version) {
        try {
            pruefeVersion(id, version);
            service.phaseLoeschen(id, phasenId);
            return ResponseEntity.noContent().build();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return notFoundOderBadRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Optimistisches Sperren (siehe {@code @Version} auf {@link Langzeitkrankmeldung},
     * analog {@code V364__aggregat_versionsspalten.sql}). Die Service-Methoden
     * aus Task 4 laden die Meldung innerhalb ihrer eigenen Transaktion immer
     * frisch aus der DB und nehmen keinen vom Client mitgeschickten Versions-
     * Parameter entgegen - ein echter DB-Level-Konflikt ueber
     * {@link ObjectOptimisticLockingFailureException} entstuende so nur bei
     * ueberlappenden Transaktionen, nicht im ueblichen Buero-Fall zweier
     * zeitlich getrennter Requests (Mitarbeiter A laedt das Formular,
     * Mitarbeiter B speichert zuerst, A speichert Minuten spaeter seine
     * inzwischen veralteten Daten). Diese Vorab-Pruefung schliesst genau diese
     * Luecke, indem sie die vom Client zuletzt gesehene Version gegen den
     * aktuellen DB-Stand vergleicht, bevor der eigentliche Schreib-Call laeuft.
     */
    private void pruefeVersion(Long id, Long erwarteteVersion) {
        Langzeitkrankmeldung aktuell = service.findeMitPhasen(id);
        if (!Objects.equals(aktuell.getVersion(), erwarteteVersion)) {
            throw new ObjectOptimisticLockingFailureException(Langzeitkrankmeldung.class, id);
        }
    }

    private ResponseEntity<Map<String, String>> notFoundOderBadRequest(IllegalArgumentException e) {
        String message = e.getMessage();
        if (message != null && message.contains("nicht gefunden")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", message));
        }
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
