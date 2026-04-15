package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Organigramm;
import org.example.kalkulationsprogramm.service.OrganigrammService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organigramme")
public class OrganigrammController {

    private final OrganigrammService service;

    public OrganigrammController(OrganigrammService service) {
        this.service = service;
    }

    /** List all saved organigramms (overview for gallery) */
    @GetMapping
    public List<OrganigrammListDto> list() {
        return service.findAll().stream()
                .map(o -> new OrganigrammListDto(o.getName(), o.getCreatedAt().toString(), o.getUpdatedAt().toString()))
                .toList();
    }

    /** Load a single organigramm by name */
    @GetMapping("/{name}")
    public ResponseEntity<OrganigrammDto> getByName(@PathVariable String name) {
        return service.findByName(name)
                .map(o -> ResponseEntity.ok(new OrganigrammDto(o.getName(), o.getContent(), o.getCreatedAt().toString(), o.getUpdatedAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create or update an organigramm */
    @PostMapping
    public OrganigrammDto save(@RequestBody OrganigrammSaveRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Name darf nicht leer sein");
        }
        Organigramm saved = service.save(req.name().trim(), req.content());
        return new OrganigrammDto(saved.getName(), saved.getContent(), saved.getCreatedAt().toString(), saved.getUpdatedAt().toString());
    }

    /** Rename an organigramm */
    @PutMapping("/{name}/rename")
    public ResponseEntity<OrganigrammDto> rename(@PathVariable String name, @RequestBody Map<String, String> body) {
        String newName = body.get("newName");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Organigramm renamed = service.rename(name, newName.trim());
            return ResponseEntity.ok(new OrganigrammDto(renamed.getName(), renamed.getContent(), renamed.getCreatedAt().toString(), renamed.getUpdatedAt().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Delete an organigramm */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        service.deleteByName(name);
        return ResponseEntity.noContent().build();
    }

    // ─── DTOs (inner records) ───────────────────────────────────────────

    record OrganigrammListDto(String name, String created, String updated) {}
    record OrganigrammDto(String name, String content, String created, String updated) {}
    record OrganigrammSaveRequest(String name, String content) {}
}
