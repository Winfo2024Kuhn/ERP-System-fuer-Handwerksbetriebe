package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * CRUD-API für Schweißer-Qualifikationszertifikate (EN ISO 9606-1 / EN ISO 14732).
 * Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@RestController
@RequestMapping("/api/schweisser-zertifikate")
@RequiredArgsConstructor
public class SchweisserZertifikatController {

    private final SchweisserZertifikatRepository repository;
    private final MitarbeiterRepository mitarbeiterRepository;

    // --- Response / Request DTOs ---

    public record ZertifikatResponse(
            Long id,
            Long mitarbeiterId,
            String mitarbeiterName,
            String zertifikatsnummer,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String pruefstelle,
            LocalDate ausstellungsdatum,
            LocalDate ablaufdatum,
            String originalDateiname,
            String gespeicherterDateiname,
            LocalDateTime erstelltAm) {
    }

    public record ZertifikatRequest(
            Long mitarbeiterId,
            String zertifikatsnummer,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String pruefstelle,
            LocalDate ausstellungsdatum,
            LocalDate ablaufdatum,
            String originalDateiname,
            String gespeicherterDateiname) {
    }

    private ZertifikatResponse toResponse(SchweisserZertifikat z) {
        String name = z.getMitarbeiter() != null
                ? z.getMitarbeiter().getVorname() + " " + z.getMitarbeiter().getNachname()
                : null;
        return new ZertifikatResponse(
                z.getId(),
                z.getMitarbeiter() != null ? z.getMitarbeiter().getId() : null,
                name,
                z.getZertifikatsnummer(),
                z.getNorm(),
                z.getSchweissProzes(),
                z.getGrundwerkstoff(),
                z.getPruefstelle(),
                z.getAusstellungsdatum(),
                z.getAblaufdatum(),
                z.getOriginalDateiname(),
                z.getGespeicherterDateiname(),
                z.getErstelltAm());
    }

    // --- Endpoints ---

    @GetMapping
    public List<ZertifikatResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZertifikatResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(z -> ResponseEntity.ok(toResponse(z)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mitarbeiter/{mitarbeiterId}")
    public List<ZertifikatResponse> getByMitarbeiter(@PathVariable Long mitarbeiterId) {
        return repository.findByMitarbeiterId(mitarbeiterId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/ablaufend")
    public List<ZertifikatResponse> getAblaufend() {
        LocalDate bis = LocalDate.now().plusDays(90);
        return repository.findAblaufendBis(bis).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ZertifikatResponse> create(@RequestBody ZertifikatRequest req) {
        SchweisserZertifikat z = new SchweisserZertifikat();
        apply(z, req);
        return ResponseEntity.ok(toResponse(repository.save(z)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZertifikatResponse> update(@PathVariable Long id,
                                                      @RequestBody ZertifikatRequest req) {
        return repository.findById(id).map(z -> {
            apply(z, req);
            return ResponseEntity.ok(toResponse(repository.save(z)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Helper ---

    private void apply(SchweisserZertifikat z, ZertifikatRequest req) {
        if (req.mitarbeiterId() != null) {
            Mitarbeiter m = mitarbeiterRepository.findById(req.mitarbeiterId())
                    .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));
            z.setMitarbeiter(m);
        }
        z.setZertifikatsnummer(req.zertifikatsnummer());
        z.setNorm(req.norm());
        z.setSchweissProzes(req.schweissProzes());
        z.setGrundwerkstoff(req.grundwerkstoff());
        z.setPruefstelle(req.pruefstelle());
        z.setAusstellungsdatum(req.ausstellungsdatum());
        z.setAblaufdatum(req.ablaufdatum());
        z.setOriginalDateiname(req.originalDateiname());
        z.setGespeicherterDateiname(req.gespeicherterDateiname());
    }
}
