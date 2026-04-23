package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.SchnittAchse;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittAchseDto;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittAchseUpsertDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.SchnittAchseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schnitt-achsen")
@RequiredArgsConstructor
public class SchnittAchseController {

    private final SchnittAchseRepository schnittAchseRepository;
    private final KategorieRepository kategorieRepository;
    private final ArtikelRepository artikelRepository;

    /**
     * Liefert Achsen zu einer Kategorie oder zu einem Artikel.
     * Priorität: artikelId > subKategorieId > kategorieId.
     * Ohne Parameter → alle Achsen (für Admin-UI).
     */
    @GetMapping
    public ResponseEntity<List<SchnittAchseDto>> list(
            @RequestParam(value = "artikelId", required = false) Long artikelId,
            @RequestParam(value = "subKategorieId", required = false) Integer subKategorieId,
            @RequestParam(value = "kategorieId", required = false) Integer kategorieId
    ) {
        Integer effectiveKategorieId = null;

        if (artikelId != null) {
            Artikel a = artikelRepository.findById(artikelId).orElse(null);
            if (a == null || a.getKategorie() == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            effectiveKategorieId = rootKategorieId(a.getKategorie());
        } else if (subKategorieId != null) {
            Kategorie k = kategorieRepository.findById(subKategorieId).orElse(null);
            if (k == null) return ResponseEntity.ok(Collections.emptyList());
            effectiveKategorieId = rootKategorieId(k);
        } else if (kategorieId != null) {
            effectiveKategorieId = kategorieId;
        }

        List<SchnittAchse> achsen = (effectiveKategorieId == null)
                ? schnittAchseRepository.findAll()
                : schnittAchseRepository.findByKategorie_IdOrderByIdAsc(effectiveKategorieId);

        List<SchnittAchseDto> result = achsen.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchnittAchseDto> get(@PathVariable Long id) {
        return schnittAchseRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SchnittAchseDto> create(@RequestBody SchnittAchseUpsertDto payload) {
        if (payload.getBildUrl() == null || payload.getBildUrl().isBlank()
                || payload.getKategorieId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Kategorie kat = kategorieRepository.findById(payload.getKategorieId()).orElse(null);
        if (kat == null) return ResponseEntity.badRequest().build();

        SchnittAchse achse = new SchnittAchse();
        achse.setBildUrl(payload.getBildUrl().trim());
        achse.setKategorie(kat);
        SchnittAchse saved = schnittAchseRepository.save(achse);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SchnittAchseDto> update(@PathVariable Long id,
                                                  @RequestBody SchnittAchseUpsertDto payload) {
        SchnittAchse achse = schnittAchseRepository.findById(id).orElse(null);
        if (achse == null) return ResponseEntity.notFound().build();

        if (payload.getBildUrl() != null && !payload.getBildUrl().isBlank()) {
            achse.setBildUrl(payload.getBildUrl().trim());
        }
        if (payload.getKategorieId() != null) {
            Kategorie kat = kategorieRepository.findById(payload.getKategorieId()).orElse(null);
            if (kat == null) return ResponseEntity.badRequest().build();
            achse.setKategorie(kat);
        }
        return ResponseEntity.ok(toDto(schnittAchseRepository.save(achse)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!schnittAchseRepository.existsById(id)) return ResponseEntity.notFound().build();
        schnittAchseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Integer rootKategorieId(Kategorie k) {
        Kategorie cur = k;
        while (cur.getParentKategorie() != null) cur = cur.getParentKategorie();
        return cur.getId();
    }

    private SchnittAchseDto toDto(SchnittAchse a) {
        SchnittAchseDto dto = new SchnittAchseDto();
        dto.setId(a.getId());
        dto.setBildUrl(a.getBildUrl());
        dto.setKategorieId(a.getKategorie() != null ? a.getKategorie().getId() : null);
        return dto;
    }
}
