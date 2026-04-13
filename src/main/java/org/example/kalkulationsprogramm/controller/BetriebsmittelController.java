package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.example.kalkulationsprogramm.domain.BetriebsmittelPruefung;
import org.example.kalkulationsprogramm.service.BetriebsmittelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-API für Betriebsmittel und E-Check Prüfprotokolle (BGV A3 / DGUV V3).
 */
@RestController
@RequestMapping("/api/betriebsmittel")
@RequiredArgsConstructor
public class BetriebsmittelController {

    private final BetriebsmittelService betriebsmittelService;

    @GetMapping
    public List<Betriebsmittel> getAll() {
        return betriebsmittelService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Betriebsmittel> getById(@PathVariable Long id) {
        return betriebsmittelService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<Betriebsmittel> getByBarcode(@PathVariable String barcode) {
        return betriebsmittelService.findByBarcode(barcode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/faellig")
    public List<Betriebsmittel> getFaellig() {
        return betriebsmittelService.findFaellig();
    }

    @PostMapping
    public Betriebsmittel create(@RequestBody Betriebsmittel betriebsmittel) {
        return betriebsmittelService.save(betriebsmittel);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Betriebsmittel> update(@PathVariable Long id,
                                                  @RequestBody Betriebsmittel betriebsmittel) {
        return betriebsmittelService.findById(id).map(existing -> {
            betriebsmittel.setId(id);
            return ResponseEntity.ok(betriebsmittelService.save(betriebsmittel));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        betriebsmittelService.findById(id).ifPresent(b -> betriebsmittelService.delete(id));
        return ResponseEntity.noContent().build();
    }

    // --- Prüfprotokolle ---

    @GetMapping("/{id}/pruefungen")
    public List<BetriebsmittelPruefung> getPruefungen(@PathVariable Long id) {
        return betriebsmittelService.findPruefungen(id);
    }

    @GetMapping("/pruefungen/offen")
    public List<BetriebsmittelPruefung> getOffenePruefungen() {
        return betriebsmittelService.findOffenePruefungen();
    }

    @PostMapping("/{id}/pruefungen")
    public BetriebsmittelPruefung pruefungErfassen(@PathVariable Long id,
                                                    @RequestParam(required = false) Long prueferId,
                                                    @RequestBody BetriebsmittelPruefung pruefung) {
        return betriebsmittelService.pruefungErfassen(id, prueferId, pruefung);
    }

    @PostMapping("/pruefungen/{pruefungId}/verifizieren")
    public ResponseEntity<BetriebsmittelPruefung> verifizieren(@PathVariable Long pruefungId) {
        return ResponseEntity.ok(betriebsmittelService.elektrikerVerifizieren(pruefungId));
    }
}
