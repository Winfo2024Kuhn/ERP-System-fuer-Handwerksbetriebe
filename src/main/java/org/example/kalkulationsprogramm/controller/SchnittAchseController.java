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
     * <p>
     * Vererbung: Wenn die gewählte Kategorie keine eigenen Achsen hat, wird
     * die Parent-Kette bis zur Wurzel durchlaufen — die erste Kategorie
     * mit Achsen gewinnt. Spezifische Unterkategorien überschreiben also die
     * Eltern vollständig, sobald sie selbst Achsen besitzen.
     */
    @GetMapping
    public ResponseEntity<List<SchnittAchseDto>> list(
            @RequestParam(value = "artikelId", required = false) Long artikelId,
            @RequestParam(value = "subKategorieId", required = false) Integer subKategorieId,
            @RequestParam(value = "kategorieId", required = false) Integer kategorieId
    ) {
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

        List<SchnittAchse> achsen = (startKategorie == null)
                ? schnittAchseRepository.findAll()
                : resolveMitVererbung(startKategorie);

        List<SchnittAchseDto> result = achsen.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Resolve die Achsen einer Kategorie unter Berücksichtigung der
     * Parent-Kette. Erste Ebene mit eigenen Achsen gewinnt.
     */
    private List<SchnittAchse> resolveMitVererbung(Kategorie start) {
        Kategorie cur = start;
        while (cur != null) {
            List<SchnittAchse> achsen = schnittAchseRepository.findByKategorie_IdOrderByIdAsc(cur.getId());
            if (!achsen.isEmpty()) return achsen;
            cur = cur.getParentKategorie();
        }
        return Collections.emptyList();
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

    private SchnittAchseDto toDto(SchnittAchse a) {
        SchnittAchseDto dto = new SchnittAchseDto();
        dto.setId(a.getId());
        dto.setBildUrl(a.getBildUrl());
        dto.setKategorieId(a.getKategorie() != null ? a.getKategorie().getId() : null);
        return dto;
    }
}
