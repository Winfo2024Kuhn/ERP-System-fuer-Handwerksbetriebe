package org.example.kalkulationsprogramm.controller.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.miete.KostenpositionDto;
import org.example.kalkulationsprogramm.dto.miete.KostenstelleDto;
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.KostenVerteilungService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/miete")
@RequiredArgsConstructor
public class KostenVerteilungController {

    private final KostenVerteilungService kostenVerteilungService;
    private final MieteMapper mapper;

    @GetMapping("/mietobjekte/{mietobjektId}/kostenstellen")
    public List<KostenstelleDto> listKostenstellen(@PathVariable Long mietobjektId) {
        return kostenVerteilungService.getKostenstellen(mietobjektId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/mietobjekte/{mietobjektId}/kostenstellen")
    @ResponseStatus(HttpStatus.CREATED)
    public KostenstelleDto createKostenstelle(@PathVariable Long mietobjektId, @RequestBody KostenstelleDto dto) {
        dto.setId(null);
        var saved = kostenVerteilungService.saveKostenstelle(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/mietobjekte/{mietobjektId}/kostenstellen/{kostenstelleId}")
    public KostenstelleDto updateKostenstelle(@PathVariable Long mietobjektId,
            @PathVariable Long kostenstelleId,
            @RequestBody KostenstelleDto dto) {
        dto.setId(kostenstelleId);
        var saved = kostenVerteilungService.saveKostenstelle(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/kostenstellen/{kostenstelleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKostenstelle(@PathVariable Long kostenstelleId) {
        kostenVerteilungService.deleteKostenstelle(kostenstelleId);
    }

    @GetMapping("/kostenstellen/{kostenstelleId}/kostenpositionen")
    public List<KostenpositionDto> listKostenpositionen(@PathVariable Long kostenstelleId,
            @RequestParam(name = "jahr", required = false) Integer jahr) {
        return kostenVerteilungService.getKostenpositionen(kostenstelleId, jahr).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/kostenstellen/{kostenstelleId}/kostenpositionen")
    @ResponseStatus(HttpStatus.CREATED)
    public KostenpositionDto createKostenposition(@PathVariable Long kostenstelleId,
            @RequestBody KostenpositionDto dto) {
        dto.setId(null);
        var saved = kostenVerteilungService.saveKostenposition(kostenstelleId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/kostenstellen/{kostenstelleId}/kostenpositionen/{kostenpositionId}")
    public KostenpositionDto updateKostenposition(@PathVariable Long kostenstelleId,
            @PathVariable Long kostenpositionId,
            @RequestBody KostenpositionDto dto) {
        dto.setId(kostenpositionId);
        var saved = kostenVerteilungService.saveKostenposition(kostenstelleId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/kostenpositionen/{kostenpositionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKostenposition(@PathVariable Long kostenpositionId) {
        kostenVerteilungService.deleteKostenposition(kostenpositionId);
    }

    @PostMapping("/mietobjekte/{mietobjektId}/kostenpositionen/copy-vorjahr")
    public java.util.Map<String, Object> copyKostenpositionenVonVorjahr(
            @PathVariable Long mietobjektId,
            @RequestParam("zielJahr") int zielJahr) {
        int kopiert = kostenVerteilungService.copyKostenpositionenVonVorjahr(mietobjektId, zielJahr);
        return java.util.Map.of("kopiert", kopiert, "zielJahr", zielJahr);
    }

    @GetMapping("/mietobjekte/{mietobjektId}/verteilungsschluessel")
    public List<VerteilungsschluesselDto> listVerteilungsschluessel(@PathVariable Long mietobjektId) {
        return kostenVerteilungService.getVerteilungsschluessel(mietobjektId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/mietobjekte/{mietobjektId}/verteilungsschluessel")
    @ResponseStatus(HttpStatus.CREATED)
    public VerteilungsschluesselDto createVerteilungsschluessel(@PathVariable Long mietobjektId,
            @RequestBody VerteilungsschluesselDto dto) {
        var saved = kostenVerteilungService.saveVerteilungsschluessel(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/mietobjekte/{mietobjektId}/verteilungsschluessel/{schluesselId}")
    public VerteilungsschluesselDto updateVerteilungsschluessel(@PathVariable Long mietobjektId,
            @PathVariable Long schluesselId,
            @RequestBody VerteilungsschluesselDto dto) {
        dto.setId(schluesselId);
        var saved = kostenVerteilungService.saveVerteilungsschluessel(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/verteilungsschluessel/{schluesselId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVerteilungsschluessel(@PathVariable Long schluesselId) {
        kostenVerteilungService.deleteVerteilungsschluessel(schluesselId);
    }
}
