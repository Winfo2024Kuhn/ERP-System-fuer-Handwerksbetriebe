package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterQualifikationDto;
import org.example.kalkulationsprogramm.service.MitarbeiterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

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

    // ==================== EN-1090-ROLLEN ENDPOINTS ====================

    @PutMapping("/{id}/en1090-rollen")
    public ResponseEntity<MitarbeiterDto> updateEn1090Rollen(
            @PathVariable Long id,
            @RequestBody java.util.List<Long> rolleIds) {
        // Rollen werden über save() gesetzt – wir bauen ein Mini-DTO
        org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto patch =
                service.findById(id)
                        .map(existing -> {
                            MitarbeiterErstellenDto d = new MitarbeiterErstellenDto();
                            d.setVorname(existing.getVorname());
                            d.setNachname(existing.getNachname());
                            d.setStrasse(existing.getStrasse());
                            d.setPlz(existing.getPlz());
                            d.setOrt(existing.getOrt());
                            d.setEmail(existing.getEmail());
                            d.setTelefon(existing.getTelefon());
                            d.setFestnetz(existing.getFestnetz());
                            d.setQualifikation(existing.getQualifikation());
                            d.setStundenlohn(existing.getStundenlohn());
                            d.setGeburtstag(existing.getGeburtstag());
                            d.setEintrittsdatum(existing.getEintrittsdatum());
                            d.setJahresUrlaub(existing.getJahresUrlaub());
                            d.setAktiv(existing.getAktiv());
                            d.setAbteilungIds(existing.getAbteilungIds());
                            d.setEn1090RolleIds(rolleIds);
                            return d;
                        })
                        .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));
        return ResponseEntity.ok(service.save(id, patch));
    }

    // ==================== QUALIFIKATIONEN ENDPOINTS ====================

    @GetMapping("/{id}/qualifikationen")
    public ResponseEntity<List<MitarbeiterQualifikationDto>> listQualifikationen(@PathVariable Long id) {
        return ResponseEntity.ok(service.listQualifikationen(id));
    }

    @PostMapping(value = "/{id}/qualifikationen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MitarbeiterQualifikationDto> createQualifikation(
            @PathVariable Long id,
            @RequestParam("bezeichnung") String bezeichnung,
            @RequestParam(value = "beschreibung", required = false) String beschreibung,
            @RequestParam(value = "datum", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum,
            @RequestParam(value = "datei", required = false) MultipartFile datei) {
        return ResponseEntity.ok(service.createQualifikation(id, bezeichnung, beschreibung, datum, datei));
    }

    @PutMapping(value = "/{id}/qualifikationen/{qualId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MitarbeiterQualifikationDto> updateQualifikation(
            @PathVariable Long id,
            @PathVariable Long qualId,
            @RequestParam("bezeichnung") String bezeichnung,
            @RequestParam(value = "beschreibung", required = false) String beschreibung,
            @RequestParam(value = "datum", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum,
            @RequestParam(value = "datei", required = false) MultipartFile datei) {
        return ResponseEntity.ok(service.updateQualifikation(id, qualId, bezeichnung, beschreibung, datum, datei));
    }

    @DeleteMapping("/{id}/qualifikationen/{qualId}")
    public ResponseEntity<Void> deleteQualifikation(@PathVariable Long id, @PathVariable Long qualId) {
        service.deleteQualifikation(id, qualId);
        return ResponseEntity.noContent().build();
    }
}
