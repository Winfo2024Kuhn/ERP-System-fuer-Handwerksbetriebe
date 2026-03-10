package org.example.kalkulationsprogramm.controller.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.miete.MietobjektDto;
import org.example.kalkulationsprogramm.dto.miete.MietparteiDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.MietobjektService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/miete")
@RequiredArgsConstructor
public class MietobjektController {

    private final MietobjektService mietobjektService;
    private final MieteMapper mapper;

    @GetMapping("/mietobjekte")
    public List<MietobjektDto> listMietobjekte() {
        return mietobjektService.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/mietobjekte")
    @ResponseStatus(HttpStatus.CREATED)
    public MietobjektDto createMietobjekt(@RequestBody MietobjektDto dto) {
        var entity = mapper.toEntity(dto);
        var saved = mietobjektService.save(entity);
        return mapper.toDto(saved);
    }

    @PutMapping("/mietobjekte/{id}")
    public MietobjektDto updateMietobjekt(@PathVariable Long id, @RequestBody MietobjektDto dto) {
        dto.setId(id);
        var updated = mietobjektService.save(mapper.toEntity(dto));
        return mapper.toDto(updated);
    }

    @DeleteMapping("/mietobjekte/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMietobjekt(@PathVariable Long id) {
        mietobjektService.delete(id);
    }

    @GetMapping("/mietobjekte/{id}/parteien")
    public List<MietparteiDto> listParteien(@PathVariable Long id) {
        return mietobjektService.getParteien(id).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/mietobjekte/{id}/parteien")
    @ResponseStatus(HttpStatus.CREATED)
    public MietparteiDto createPartei(@PathVariable Long id, @RequestBody MietparteiDto dto) {
        var saved = mietobjektService.savePartei(id, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/mietobjekte/{id}/parteien/{parteiId}")
    public MietparteiDto updatePartei(@PathVariable Long id, @PathVariable Long parteiId, @RequestBody MietparteiDto dto) {
        dto.setId(parteiId);
        var saved = mietobjektService.savePartei(id, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/parteien/{parteiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePartei(@PathVariable Long parteiId) {
        mietobjektService.deletePartei(parteiId);
    }
}
