package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.CreateReklamationRequest;
import org.example.kalkulationsprogramm.dto.LieferantReklamationDto;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.controller.LieferantenController.LieferantBildDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reklamationen")
@RequiredArgsConstructor
public class LieferantReklamationController {

    private final LieferantReklamationRepository reklamationRepository;
    private final LieferantenRepository lieferantenRepository;
    private final LieferantDokumentRepository dokumentRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final LieferantBildRepository bildRepository;

    @GetMapping("/lieferant/{lieferantId}")
    public ResponseEntity<List<LieferantReklamationDto>> getByLieferant(@PathVariable Long lieferantId) {
        if (!lieferantenRepository.existsById(lieferantId)) {
            return ResponseEntity.notFound().build();
        }
        var list = reklamationRepository.findByLieferantIdOrderByStatusAscErstelltAmDesc(lieferantId);
        return ResponseEntity.ok(list.stream().map(this::mapToDto).collect(Collectors.toList()));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam ReklamationStatus status) {
        var reklamation = reklamationRepository.findById(id).orElse(null);
        if (reklamation == null) {
            return ResponseEntity.notFound().build();
        }
        reklamation.setStatus(status);
        reklamationRepository.save(reklamation);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!reklamationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reklamationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lieferant/{lieferantId}")
    @Transactional
    public ResponseEntity<LieferantReklamationDto> create(
            @PathVariable Long lieferantId,
            @RequestBody CreateReklamationRequest request,
            @RequestParam(required = false) String token) {

        var lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        var mitarbeiter = mitarbeiterByToken(token);

        LieferantReklamation reklamation = new LieferantReklamation();
        reklamation.setLieferant(lieferant);
        reklamation.setErstelltVon(mitarbeiter);
        reklamation.setBeschreibung(request.getBeschreibung());
        reklamation.setStatus(request.getStatus() != null ? request.getStatus() : ReklamationStatus.OFFEN);

        if (request.getLieferscheinId() != null) {
            var lieferschein = dokumentRepository.findById(request.getLieferscheinId()).orElse(null);
            if (lieferschein != null && lieferschein.getLieferant().getId().equals(lieferantId)) {
                reklamation.setLieferschein(lieferschein);
            }
        }

        reklamation = reklamationRepository.save(reklamation);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(reklamation));
    }

    @PostMapping(value = "/{id}/bilder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<LieferantBildDto> uploadBild(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei,
            @RequestParam(required = false) String token) {

        var reklamation = reklamationRepository.findById(id).orElse(null);
        if (reklamation == null) {
            return ResponseEntity.notFound().build();
        }

        var mitarbeiter = mitarbeiterByToken(token);

        try {
            String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
            String storedFilename = UUID.randomUUID() + "_" + originalFilename;

            // Reuse the existing supplier images folder structure
            Path lieferantDir = Paths.get("uploads", "lieferanten",
                    reklamation.getLieferant().getId().toString(), "bilder");
            Files.createDirectories(lieferantDir);
            Path targetPath = lieferantDir.resolve(storedFilename);
            datei.transferTo(targetPath);

            LieferantBild bild = new LieferantBild();
            bild.setLieferant(reklamation.getLieferant());
            bild.setReklamation(reklamation); // Link to reclamation
            bild.setOriginalDateiname(originalFilename);
            bild.setGespeicherterDateiname(storedFilename);
            bild.setErstelltAm(java.time.LocalDateTime.now());
            bild.setHochgeladenVon(mitarbeiter);

            bild = bildRepository.save(bild);

            LieferantBildDto dto = new LieferantBildDto();
            dto.id = bild.getId();
            dto.originalDateiname = bild.getOriginalDateiname();
            dto.url = "/api/lieferanten/bilder/file/" + bild.getGespeicherterDateiname();
            dto.erstelltAm = bild.getErstelltAm();
            if (mitarbeiter != null) {
                dto.mitarbeiterVorname = mitarbeiter.getVorname();
                dto.mitarbeiterNachname = mitarbeiter.getNachname();
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/lieferscheine/search")
    public ResponseEntity<List<LieferscheinSearchDto>> searchLieferscheine(
            @RequestParam Long lieferantId,
            @RequestParam String query) {

        var results = dokumentRepository.searchLieferscheine(lieferantId, query);
        var dtos = results.stream().map(doc -> {
            LieferscheinSearchDto dto = new LieferscheinSearchDto();
            dto.setId(doc.getId());
            dto.setOriginalDateiname(doc.getEffektiverDateiname());
            if (doc.getGeschaeftsdaten() != null) {
                dto.setDokumentNummer(doc.getGeschaeftsdaten().getDokumentNummer());
                dto.setDatum(doc.getGeschaeftsdaten().getDokumentDatum());
            } else {
                dto.setDatum(doc.getUploadDatum().toLocalDate());
            }
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private LieferantReklamationDto mapToDto(LieferantReklamation entity) {
        LieferantReklamationDto dto = new LieferantReklamationDto();
        dto.setId(entity.getId());
        dto.setLieferantId(entity.getLieferant().getId());
        dto.setLieferantName(entity.getLieferant().getLieferantenname());

        if (entity.getLieferschein() != null) {
            dto.setLieferscheinId(entity.getLieferschein().getId());
            dto.setLieferscheinDateiname(entity.getLieferschein().getEffektiverDateiname());
            if (entity.getLieferschein().getGeschaeftsdaten() != null) {
                dto.setLieferscheinNummer(entity.getLieferschein().getGeschaeftsdaten().getDokumentNummer());
            }
        }

        if (entity.getErstelltVon() != null) {
            dto.setErstellerName(entity.getErstelltVon().getVorname() + " " + entity.getErstelltVon().getNachname());
        }

        dto.setErstelltAm(entity.getErstelltAm());
        dto.setBeschreibung(entity.getBeschreibung());
        dto.setStatus(entity.getStatus());

        dto.setBilder(entity.getBilder().stream().map(b -> {
            LieferantBildDto img = new LieferantBildDto();
            img.id = b.getId();
            img.originalDateiname = b.getOriginalDateiname();
            img.url = "/api/lieferanten/bilder/file/" + b.getGespeicherterDateiname();
            img.beschreibung = b.getBeschreibung();
            img.erstelltAm = b.getErstelltAm();
            if (b.getHochgeladenVon() != null) {
                img.mitarbeiterVorname = b.getHochgeladenVon().getVorname();
                img.mitarbeiterNachname = b.getHochgeladenVon().getNachname();
            }
            return img;
        }).collect(Collectors.toList()));

        return dto;
    }

    private Mitarbeiter mitarbeiterByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return mitarbeiterRepository.findByLoginToken(token).orElse(null);
    }

    @lombok.Data
    public static class LieferscheinSearchDto {
        private Long id;
        private String dokumentNummer;
        private String originalDateiname;
        private java.time.LocalDate datum;
    }
}
