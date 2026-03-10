package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Schnittbilder;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittbildResponseDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schnittbilder")
@RequiredArgsConstructor
public class SchnittbilderController {

    private final SchnittbilderRepository schnittbilderRepository;
    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;

    /**
     * Liefert passende Schnittbilder abhängig von Artikel- oder Kategorieauswahl.
     * Regeln:
     * - Nur wenn die Wurzelkategorie 64 oder 65 ist (oder deren Unterkategorien)
     * Priorität der Parameter: artikelId > subKategorieId > kategorieId (Root)
     */
    @GetMapping
    public ResponseEntity<List<SchnittbildResponseDto>> list(
            @RequestParam(value = "artikelId", required = false) Long artikelId,
            @RequestParam(value = "subKategorieId", required = false) Integer subKategorieId,
            @RequestParam(value = "kategorieId", required = false) Integer kategorieId
    ) {
        Integer effectiveRoot = null;

        if (artikelId != null) {
            Artikel a = artikelRepository.findById(artikelId).orElse(null);
            if (a == null || a.getKategorie() == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            Integer rootId = rootKategorieId(a.getKategorie());
            if (!isAllowedRoot(rootId)) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            effectiveRoot = rootId;
        } else if (subKategorieId != null) {
            Kategorie k = kategorieRepository.findById(subKategorieId).orElse(null);
            if (k == null) return ResponseEntity.ok(Collections.emptyList());
            Integer rootId = rootKategorieId(k);
            if (!isAllowedRoot(rootId)) return ResponseEntity.ok(Collections.emptyList());
            effectiveRoot = rootId;
        } else if (kategorieId != null) {
            effectiveRoot = kategorieId;
        }

        if (!isAllowedRoot(effectiveRoot)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<SchnittbildResponseDto> result = schnittbilderRepository.findByKategorie_Id(effectiveRoot)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchnittbildResponseDto> get(@PathVariable Long id) {
        return schnittbilderRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isAllowedRoot(Integer id) {
        return id != null && (id == 64 || id == 65);
    }

    private Integer rootKategorieId(Kategorie k) {
        Kategorie cur = k;
        while (cur.getParentKategorie() != null) cur = cur.getParentKategorie();
        return cur.getId();
    }

    private SchnittbildResponseDto toDto(Schnittbilder s) {
        SchnittbildResponseDto dto = new SchnittbildResponseDto();
        dto.setId(s.getId());
        dto.setBildUrlSchnittbild(s.getBildUrlSchnittbild());
        dto.setForm(s.getForm());
        dto.setKategorieId(s.getKategorie() != null ? s.getKategorie().getId() : null);
        return dto;
    }
}
