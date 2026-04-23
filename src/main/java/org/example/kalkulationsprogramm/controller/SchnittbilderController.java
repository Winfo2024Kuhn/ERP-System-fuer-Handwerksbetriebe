package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.SchnittAchse;
import org.example.kalkulationsprogramm.domain.Schnittbilder;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittbildResponseDto;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittbildUpsertDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.SchnittAchseRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schnittbilder")
@RequiredArgsConstructor
public class SchnittbilderController {

    private final SchnittbilderRepository schnittbilderRepository;
    private final SchnittAchseRepository schnittAchseRepository;
    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final DateiSpeicherService dateiSpeicherService;

    /**
     * Bild-Upload für Achsen und Schnittbilder. Nimmt ein Bild entgegen,
     * speichert es über DateiSpeicherService und liefert die URL zurück,
     * die anschließend beim Anlegen einer Achse oder eines Schnittbilds
     * als {@code bildUrl} verwendet wird.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("datei") MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String url = dateiSpeicherService.speichereBild(datei);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Schnittbild-Liste. Priorität der Parameter:
     *   schnittAchseId > artikelId > subKategorieId > kategorieId.
     * Ohne Parameter → leere Liste (Admin soll explizit filtern).
     * <p>
     * Bei kategorie-basiertem Zugriff gilt Vererbung wie bei den Achsen:
     * erste Kategorie in der Parent-Kette, die Schnittbilder hat, gewinnt.
     */
    @GetMapping
    public ResponseEntity<List<SchnittbildResponseDto>> list(
            @RequestParam(value = "schnittAchseId", required = false) Long schnittAchseId,
            @RequestParam(value = "artikelId", required = false) Long artikelId,
            @RequestParam(value = "subKategorieId", required = false) Integer subKategorieId,
            @RequestParam(value = "kategorieId", required = false) Integer kategorieId
    ) {
        List<Schnittbilder> rows;

        if (schnittAchseId != null) {
            rows = schnittbilderRepository.findBySchnittAchse_IdOrderByIdAsc(schnittAchseId);
        } else {
            Kategorie startKategorie = null;
            if (artikelId != null) {
                Artikel a = artikelRepository.findById(artikelId).orElse(null);
                if (a == null || a.getKategorie() == null) {
                    return ResponseEntity.ok(Collections.emptyList());
                }
                startKategorie = a.getKategorie();
            } else if (subKategorieId != null) {
                startKategorie = kategorieRepository.findById(subKategorieId).orElse(null);
                if (startKategorie == null) return ResponseEntity.ok(Collections.emptyList());
            } else if (kategorieId != null) {
                startKategorie = kategorieRepository.findById(kategorieId).orElse(null);
                if (startKategorie == null) return ResponseEntity.ok(Collections.emptyList());
            }

            if (startKategorie == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            rows = resolveMitVererbung(startKategorie);
        }

        List<SchnittbildResponseDto> result = rows.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Erste Kategorie in der Parent-Kette, die Schnittbilder besitzt, gewinnt
     * (gleiche Logik wie bei den Achsen — spezifische Unterkategorien
     * überschreiben die Eltern vollständig, sobald sie eigene Einträge haben).
     */
    private List<Schnittbilder> resolveMitVererbung(Kategorie start) {
        Kategorie cur = start;
        while (cur != null) {
            List<Schnittbilder> found = schnittbilderRepository.findBySchnittAchse_Kategorie_IdOrderByIdAsc(cur.getId());
            if (!found.isEmpty()) return found;
            cur = cur.getParentKategorie();
        }
        return Collections.emptyList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchnittbildResponseDto> get(@PathVariable Long id) {
        return schnittbilderRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SchnittbildResponseDto> create(@RequestBody SchnittbildUpsertDto payload) {
        if (payload.getBildUrlSchnittbild() == null || payload.getBildUrlSchnittbild().isBlank()
                || payload.getSchnittAchseId() == null) {
            return ResponseEntity.badRequest().build();
        }
        SchnittAchse achse = schnittAchseRepository.findById(payload.getSchnittAchseId()).orElse(null);
        if (achse == null) return ResponseEntity.badRequest().build();

        Schnittbilder sb = new Schnittbilder();
        sb.setBildUrlSchnittbild(payload.getBildUrlSchnittbild().trim());
        sb.setSchnittAchse(achse);
        Schnittbilder saved = schnittbilderRepository.save(sb);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SchnittbildResponseDto> update(@PathVariable Long id,
                                                        @RequestBody SchnittbildUpsertDto payload) {
        Schnittbilder sb = schnittbilderRepository.findById(id).orElse(null);
        if (sb == null) return ResponseEntity.notFound().build();

        if (payload.getBildUrlSchnittbild() != null && !payload.getBildUrlSchnittbild().isBlank()) {
            sb.setBildUrlSchnittbild(payload.getBildUrlSchnittbild().trim());
        }
        if (payload.getSchnittAchseId() != null) {
            SchnittAchse achse = schnittAchseRepository.findById(payload.getSchnittAchseId()).orElse(null);
            if (achse == null) return ResponseEntity.badRequest().build();
            sb.setSchnittAchse(achse);
        }
        return ResponseEntity.ok(toDto(schnittbilderRepository.save(sb)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!schnittbilderRepository.existsById(id)) return ResponseEntity.notFound().build();
        schnittbilderRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private SchnittbildResponseDto toDto(Schnittbilder s) {
        SchnittbildResponseDto dto = new SchnittbildResponseDto();
        dto.setId(s.getId());
        dto.setBildUrlSchnittbild(s.getBildUrlSchnittbild());
        SchnittAchse a = s.getSchnittAchse();
        if (a != null) {
            dto.setSchnittAchseId(a.getId());
            dto.setSchnittAchseBildUrl(a.getBildUrl());
            if (a.getKategorie() != null) {
                dto.setKategorieId(a.getKategorie().getId());
            }
        }
        return dto;
    }
}
