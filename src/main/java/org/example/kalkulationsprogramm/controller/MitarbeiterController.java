package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.MitarbeiterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
    private final BelegService belegService;

    @GetMapping
    public ResponseEntity<List<MitarbeiterDto>> list(Authentication authentication) {
        Zugang zugang = zugang(authentication);
        List<MitarbeiterDto> liste = service.list();
        liste.forEach(zugang::verbergeFremdenCode);
        return ResponseEntity.ok(liste);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MitarbeiterDto> get(@PathVariable Long id, Authentication authentication) {
        Zugang zugang = zugang(authentication);
        return service.findById(id)
                .map(zugang::verbergeFremdenCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MitarbeiterDto> create(@RequestBody MitarbeiterErstellenDto dto, Authentication authentication) {
        return ResponseEntity.ok(zugang(authentication).verbergeFremdenCode(service.save(null, dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MitarbeiterDto> update(@PathVariable Long id, @RequestBody MitarbeiterErstellenDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(zugang(authentication).verbergeFremdenCode(service.save(id, dto)));
    }

    /**
     * Wer darf Anmelde-Codes (Login-Tokens) sehen? Mit einem Code meldet man sich in der Handy-App als
     * dieser Mitarbeiter an – auch von außerhalb. Deshalb nur Admins und der Mitarbeiter selbst.
     */
    private record Zugang(boolean admin, Long eigeneMitarbeiterId) {
        boolean darfCodeSehen(Long mitarbeiterId) {
            return admin || (eigeneMitarbeiterId != null && eigeneMitarbeiterId.equals(mitarbeiterId));
        }

        MitarbeiterDto verbergeFremdenCode(MitarbeiterDto dto) {
            if (dto != null && !darfCodeSehen(dto.getId())) {
                dto.setLoginToken(null);
            }
            return dto;
        }
    }

    private Zugang zugang(Authentication authentication) {
        boolean admin = authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal p
                && p.hasRole(FrontendUserRole.ADMIN);
        Mitarbeiter eigener = admin ? null : belegService.findCaller(null, authentication);
        return new Zugang(admin, eigener != null ? eigener.getId() : null);
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
            @RequestParam(defaultValue = "300") int height,
            Authentication authentication) {
        if (!zugang(authentication).darfCodeSehen(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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
    public ResponseEntity<String> regenerateToken(@PathVariable Long id, Authentication authentication) {
        if (!zugang(authentication).darfCodeSehen(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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

    // ==================== STUNDENLOHN-HISTORIE ENDPOINTS ====================

    @GetMapping("/{id}/stundenlohn-historie")
    public ResponseEntity<List<org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterStundenlohnDto>> listStundenloehne(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.listStundenloehne(id));
    }

    @PostMapping("/{id}/stundenlohn-historie")
    public ResponseEntity<?> addStundenlohn(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterStundenlohnDto dto) {
        try {
            return ResponseEntity.ok(service.addStundenlohn(id, dto));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/stundenlohn-historie/{eintragId}")
    public ResponseEntity<?> updateStundenlohn(
            @PathVariable Long eintragId,
            @jakarta.validation.Valid @RequestBody org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterStundenlohnDto dto) {
        try {
            return ResponseEntity.ok(service.updateStundenlohn(eintragId, dto));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/stundenlohn-historie/{eintragId}")
    public ResponseEntity<Void> deleteStundenlohn(@PathVariable Long eintragId) {
        service.deleteStundenlohn(eintragId);
        return ResponseEntity.noContent().build();
    }
}
