package org.example.kalkulationsprogramm.controller.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.miete.RaumDto;
import org.example.kalkulationsprogramm.dto.miete.VerbrauchsgegenstandDto;
import org.example.kalkulationsprogramm.dto.miete.ZaehlerstandDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.RaumVerbrauchService;
import org.example.kalkulationsprogramm.service.miete.ZaehlerstandErfassungsPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class RaumVerbrauchController {

    private final RaumVerbrauchService raumVerbrauchService;
    private final MieteMapper mapper;
    private final ZaehlerstandErfassungsPdfService zaehlerstandErfassungsPdfService;

    @GetMapping("/mietobjekte/{mietobjektId}/raeume")
    public List<RaumDto> listRaeume(@PathVariable Long mietobjektId) {
        return raumVerbrauchService.getRaeume(mietobjektId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/mietobjekte/{mietobjektId}/raeume")
    @ResponseStatus(HttpStatus.CREATED)
    public RaumDto createRaum(@PathVariable Long mietobjektId, @RequestBody RaumDto dto) {
        var saved = raumVerbrauchService.saveRaum(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/mietobjekte/{mietobjektId}/raeume/{raumId}")
    public RaumDto updateRaum(@PathVariable Long mietobjektId, @PathVariable Long raumId, @RequestBody RaumDto dto) {
        dto.setId(raumId);
        var saved = raumVerbrauchService.saveRaum(mietobjektId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/raeume/{raumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRaum(@PathVariable Long raumId) {
        raumVerbrauchService.deleteRaum(raumId);
    }

    @GetMapping("/raeume/{raumId}/verbrauchsgegenstaende")
    public List<VerbrauchsgegenstandDto> listVerbrauchsgegenstaende(@PathVariable Long raumId) {
        return raumVerbrauchService.getVerbrauchsgegenstaende(raumId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/raeume/{raumId}/verbrauchsgegenstaende")
    @ResponseStatus(HttpStatus.CREATED)
    public VerbrauchsgegenstandDto createVerbrauchsgegenstand(@PathVariable Long raumId, @RequestBody VerbrauchsgegenstandDto dto) {
        var saved = raumVerbrauchService.saveVerbrauchsgegenstand(raumId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/raeume/{raumId}/verbrauchsgegenstaende/{gegenstandId}")
    public VerbrauchsgegenstandDto updateVerbrauchsgegenstand(@PathVariable Long raumId,
                                                              @PathVariable Long gegenstandId,
                                                              @RequestBody VerbrauchsgegenstandDto dto) {
        dto.setId(gegenstandId);
        var saved = raumVerbrauchService.saveVerbrauchsgegenstand(raumId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/verbrauchsgegenstaende/{gegenstandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVerbrauchsgegenstand(@PathVariable Long gegenstandId) {
        raumVerbrauchService.deleteVerbrauchsgegenstand(gegenstandId);
    }

    @GetMapping(value = "/mietobjekte/{mietobjektId}/zaehlerstaende/erfassung.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadZaehlerstandErfassungsbogen(@PathVariable Long mietobjektId,
                                                                      @RequestParam(value = "jahr", required = false) Integer jahr) {
        byte[] pdf = zaehlerstandErfassungsPdfService.generatePdf(mietobjektId, jahr);
        int yearForName = jahr != null ? jahr : java.time.LocalDate.now().getYear();
        String filename = "zaehlerstaende-ablesebogen-" + mietobjektId + "-" + yearForName + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende")
    public List<ZaehlerstandDto> listZaehlerstaende(@PathVariable Long gegenstandId) {
        return raumVerbrauchService.getZaehlerstaende(gegenstandId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PostMapping("/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende")
    @ResponseStatus(HttpStatus.CREATED)
    public ZaehlerstandDto createZaehlerstand(@PathVariable Long gegenstandId, @RequestBody ZaehlerstandDto dto) {
        var saved = raumVerbrauchService.saveZaehlerstand(gegenstandId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @PutMapping("/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende/{zaehlerstandId}")
    public ZaehlerstandDto updateZaehlerstand(@PathVariable Long gegenstandId,
                                              @PathVariable Long zaehlerstandId,
                                              @RequestBody ZaehlerstandDto dto) {
        dto.setId(zaehlerstandId);
        var saved = raumVerbrauchService.saveZaehlerstand(gegenstandId, mapper.toEntity(dto));
        return mapper.toDto(saved);
    }

    @DeleteMapping("/zaehlerstaende/{zaehlerstandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZaehlerstand(@PathVariable Long zaehlerstandId) {
        raumVerbrauchService.deleteZaehlerstand(zaehlerstandId);
    }
}
