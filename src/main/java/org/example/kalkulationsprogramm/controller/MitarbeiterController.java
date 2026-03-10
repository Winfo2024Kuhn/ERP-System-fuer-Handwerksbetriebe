package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.service.MitarbeiterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/mitarbeiter")
@RequiredArgsConstructor
public class MitarbeiterController {

    private final MitarbeiterService service;

    @GetMapping
    public ResponseEntity<List<MitarbeiterDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MitarbeiterDto> get(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MitarbeiterDto> create(@RequestBody MitarbeiterErstellenDto dto) {
        return ResponseEntity.ok(service.save(null, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MitarbeiterDto> update(@PathVariable Long id, @RequestBody MitarbeiterErstellenDto dto) {
        return ResponseEntity.ok(service.save(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/{id}/dokumente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MitarbeiterDokumentResponseDto> uploadDokument(@PathVariable Long id,
            @RequestParam("datei") MultipartFile datei,
            @RequestParam(value = "gruppe", required = false) DokumentGruppe gruppe) {
        return ResponseEntity.ok(service.uploadDokument(id, datei, gruppe));
    }

    @GetMapping("/{id}/dokumente")
    public ResponseEntity<List<MitarbeiterDokumentResponseDto>> listDokumente(@PathVariable Long id) {
        return ResponseEntity.ok(service.listDokumente(id));
    }

    // ==================== QR-CODE ENDPOINTS ====================

    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable Long id,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "300") int height) {
        try {
            byte[] qrCode = service.generateQrCode(id, width, height);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(qrCode);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/regenerate-token")
    public ResponseEntity<String> regenerateToken(@PathVariable Long id) {
        try {
            String newToken = service.generateLoginToken(id);
            return ResponseEntity.ok(newToken);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/by-token/{token}")
    public ResponseEntity<MitarbeiterDto> getByToken(@PathVariable String token) {
        return service.findByToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== NOTIZEN ENDPOINTS ====================

    @GetMapping("/{id}/notizen")
    public ResponseEntity<List<org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto>> listNotizen(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.listNotizen(id));
    }

    @PostMapping("/{id}/notizen")
    public ResponseEntity<org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto> createNotiz(
            @PathVariable Long id, @RequestBody String inhalt) {
        return ResponseEntity.ok(service.createNotiz(id, inhalt));
    }

    @DeleteMapping("/notizen/{notizId}")
    public ResponseEntity<Void> deleteNotiz(@PathVariable Long notizId) {
        service.deleteNotiz(notizId);
        return ResponseEntity.noContent().build();
    }
}
