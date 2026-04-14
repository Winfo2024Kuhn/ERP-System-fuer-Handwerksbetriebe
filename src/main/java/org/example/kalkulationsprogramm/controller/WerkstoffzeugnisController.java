package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Werkstoffzeugnis;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffzeugnisRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
 * CRUD-API für Werkstoffzeugnisse nach EN 10204 (Typen 2.1, 2.2, 3.1, 3.2).
 * Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@RestController
@RequestMapping("/api/werkstoffzeugnisse")
@RequiredArgsConstructor
public class WerkstoffzeugnisController {

    private final WerkstoffzeugnisRepository repository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;

    // --- Response / Request DTOs ---

    public record WerkstoffzeugnisResponse(
            Long id,
            Long lieferantId,
            String lieferantName,
            String schmelzNummer,
            String materialGuete,
            String normTyp,
            LocalDate pruefDatum,
            String pruefstelle,
            String originalDateiname,
            String gespeicherterDateiname,
            Long lieferscheinDokumentId,
            String lieferscheinNummer,
            LocalDateTime erstelltAm) {
    }

    public record WerkstoffzeugnisRequest(
            Long lieferantId,
            String schmelzNummer,
            String materialGuete,
            String normTyp,
            LocalDate pruefDatum,
            String pruefstelle,
            String originalDateiname,
            String gespeicherterDateiname) {
    }

    private WerkstoffzeugnisResponse toResponse(Werkstoffzeugnis w) {
        var ls = w.getLieferscheinDokument();
        return new WerkstoffzeugnisResponse(
                w.getId(),
                w.getLieferant() != null ? w.getLieferant().getId() : null,
                w.getLieferant() != null ? w.getLieferant().getLieferantenname() : null,
                w.getSchmelzNummer(),
                w.getMaterialGuete(),
                w.getNormTyp(),
                w.getPruefDatum(),
                w.getPruefstelle(),
                w.getOriginalDateiname(),
                w.getGespeicherterDateiname(),
                ls != null ? ls.getId() : null,
                ls != null && ls.getGeschaeftsdaten() != null
                        ? ls.getGeschaeftsdaten().getDokumentNummer() : null,
                w.getErstelltAm());
    }

    // --- Endpoints ---

    @GetMapping
    public List<WerkstoffzeugnisResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WerkstoffzeugnisResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(w -> ResponseEntity.ok(toResponse(w)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projekt/{projektId}")
    public List<WerkstoffzeugnisResponse> getByProjekt(@PathVariable Long projektId) {
        return repository.findByProjektId(projektId).stream().map(this::toResponse).toList();
    }

    /** Alle Werkstoffzeugnisse zu einem Lieferschein (1:N) */
    @GetMapping("/lieferschein/{dokumentId}")
    public List<WerkstoffzeugnisResponse> getByLieferschein(@PathVariable Long dokumentId) {
        return repository.findByLieferscheinDokumentId(dokumentId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/lieferant/{lieferantId}")
    public List<WerkstoffzeugnisResponse> getByLieferant(@PathVariable Long lieferantId) {
        return repository.findByLieferantId(lieferantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<WerkstoffzeugnisResponse> create(@RequestBody WerkstoffzeugnisRequest req) {
        Werkstoffzeugnis w = new Werkstoffzeugnis();
        apply(w, req);
        return ResponseEntity.ok(toResponse(repository.save(w)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WerkstoffzeugnisResponse> update(@PathVariable Long id,
                                                            @RequestBody WerkstoffzeugnisRequest req) {
        return repository.findById(id).map(w -> {
            apply(w, req);
            return ResponseEntity.ok(toResponse(repository.save(w)));
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

    // --- Projekt-Zuweisung ---

    /** Werkstoffzeugnis einem Projekt zuordnen */
    @PostMapping("/{id}/projekt/{projektId}")
    @Transactional
    public ResponseEntity<Void> assignToProjekt(@PathVariable Long id,
                                                 @PathVariable Long projektId) {
        Werkstoffzeugnis w = repository.findById(id).orElse(null);
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (w == null || projekt == null) return ResponseEntity.notFound().build();
        w.getProjekte().add(projekt);
        repository.save(w);
        return ResponseEntity.ok().build();
    }

    /** Werkstoffzeugnis aus Projekt entfernen */
    @DeleteMapping("/{id}/projekt/{projektId}")
    @Transactional
    public ResponseEntity<Void> unassignFromProjekt(@PathVariable Long id,
                                                     @PathVariable Long projektId) {
        Werkstoffzeugnis w = repository.findById(id).orElse(null);
        if (w == null) return ResponseEntity.notFound().build();
        w.getProjekte().removeIf(p -> p.getId().equals(projektId));
        repository.save(w);
        return ResponseEntity.noContent().build();
    }

    // --- Helper ---

    private void apply(Werkstoffzeugnis w, WerkstoffzeugnisRequest req) {
        if (req.lieferantId() != null) {
            Lieferanten l = lieferantenRepository.findById(req.lieferantId())
                    .orElseThrow(() -> new IllegalArgumentException("Lieferant nicht gefunden"));
            w.setLieferant(l);
        } else {
            w.setLieferant(null);
        }
        w.setSchmelzNummer(req.schmelzNummer());
        w.setMaterialGuete(req.materialGuete());
        w.setNormTyp(req.normTyp() != null ? req.normTyp() : "3.1");
        w.setPruefDatum(req.pruefDatum());
        w.setPruefstelle(req.pruefstelle());
        w.setOriginalDateiname(req.originalDateiname());
        w.setGespeicherterDateiname(req.gespeicherterDateiname());
    }
}
