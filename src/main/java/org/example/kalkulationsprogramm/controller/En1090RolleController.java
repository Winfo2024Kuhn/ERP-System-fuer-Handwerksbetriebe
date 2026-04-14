package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.En1090Rolle;
import org.example.kalkulationsprogramm.dto.En1090RolleDto;
import org.example.kalkulationsprogramm.repository.En1090RolleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/en1090/rollen")
@RequiredArgsConstructor
public class En1090RolleController {

    private final En1090RolleRepository repository;

    @GetMapping
    public ResponseEntity<List<En1090RolleDto>> list() {
        return ResponseEntity.ok(
                repository.findAllByOrderBySortierungAsc().stream()
                        .map(this::mapToDto)
                        .collect(Collectors.toList())
        );
    }

    @PostMapping
    public ResponseEntity<En1090RolleDto> create(@RequestBody En1090RolleDto dto) {
        En1090Rolle entity = new En1090Rolle();
        applyDto(dto, entity);
        return ResponseEntity.ok(mapToDto(repository.save(entity)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<En1090RolleDto> update(@PathVariable Long id, @RequestBody En1090RolleDto dto) {
        return repository.findById(id)
                .map(entity -> {
                    applyDto(dto, entity);
                    return ResponseEntity.ok(mapToDto(repository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------

    private void applyDto(En1090RolleDto dto, En1090Rolle entity) {
        entity.setKurztext(dto.getKurztext());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setSortierung(dto.getSortierung() != null ? dto.getSortierung() : 0);
        entity.setAktiv(dto.getAktiv() != null ? dto.getAktiv() : true);
    }

    private En1090RolleDto mapToDto(En1090Rolle e) {
        En1090RolleDto dto = new En1090RolleDto();
        dto.setId(e.getId());
        dto.setKurztext(e.getKurztext());
        dto.setBeschreibung(e.getBeschreibung());
        dto.setSortierung(e.getSortierung());
        dto.setAktiv(e.getAktiv());
        return dto;
    }
}
