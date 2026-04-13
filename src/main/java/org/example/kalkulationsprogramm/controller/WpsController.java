package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.WpsRepository;
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
 * CRUD-API für Schweißanweisungen (WPS – Welding Procedure Specification)
 * nach EN ISO 15614-1. Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@RestController
@RequestMapping("/api/wps")
@RequiredArgsConstructor
public class WpsController {

    private final WpsRepository repository;

    // --- Response / Request DTOs ---

    public record WpsResponse(
            Long id,
            String wpsNummer,
            String bezeichnung,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String zusatzwerkstoff,
            String nahtart,
            BigDecimal blechdickeMin,
            BigDecimal blechdickeMax,
            LocalDate revisionsdatum,
            LocalDate gueltigBis,
            String originalDateiname,
            String gespeicherterDateiname,
            LocalDateTime erstelltAm) {
    }

    public record WpsRequest(
            String wpsNummer,
            String bezeichnung,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String zusatzwerkstoff,
            String nahtart,
            BigDecimal blechdickeMin,
            BigDecimal blechdickeMax,
            LocalDate revisionsdatum,
            LocalDate gueltigBis,
            String originalDateiname,
            String gespeicherterDateiname) {
    }

    private WpsResponse toResponse(Wps w) {
        return new WpsResponse(
                w.getId(),
                w.getWpsNummer(),
                w.getBezeichnung(),
                w.getNorm(),
                w.getSchweissProzes(),
                w.getGrundwerkstoff(),
                w.getZusatzwerkstoff(),
                w.getNahtart(),
                w.getBlechdickeMin(),
                w.getBlechdickeMax(),
                w.getRevisionsdatum(),
                w.getGueltigBis(),
                w.getOriginalDateiname(),
                w.getGespeicherterDateiname(),
                w.getErstelltAm());
    }

    // --- Endpoints ---

    @GetMapping
    public List<WpsResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WpsResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(w -> ResponseEntity.ok(toResponse(w)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projekt/{projektId}")
    public List<WpsResponse> getByProjekt(@PathVariable Long projektId) {
        return repository.findByProjektId(projektId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<WpsResponse> create(@RequestBody WpsRequest req) {
        Wps w = new Wps();
        apply(w, req);
        return ResponseEntity.ok(toResponse(repository.save(w)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WpsResponse> update(@PathVariable Long id,
                                               @RequestBody WpsRequest req) {
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

    // --- Helper ---

    private void apply(Wps w, WpsRequest req) {
        w.setWpsNummer(req.wpsNummer());
        w.setBezeichnung(req.bezeichnung());
        w.setNorm(req.norm());
        w.setSchweissProzes(req.schweissProzes());
        w.setGrundwerkstoff(req.grundwerkstoff());
        w.setZusatzwerkstoff(req.zusatzwerkstoff());
        w.setNahtart(req.nahtart());
        w.setBlechdickeMin(req.blechdickeMin());
        w.setBlechdickeMax(req.blechdickeMax());
        w.setRevisionsdatum(req.revisionsdatum());
        w.setGueltigBis(req.gueltigBis());
        w.setOriginalDateiname(req.originalDateiname());
        w.setGespeicherterDateiname(req.gespeicherterDateiname());
    }
}
