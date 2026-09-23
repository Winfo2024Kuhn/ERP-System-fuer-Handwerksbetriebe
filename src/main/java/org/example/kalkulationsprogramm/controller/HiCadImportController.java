package org.example.kalkulationsprogramm.controller;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService.SpaltenMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/einkauf/hicad")
@RequiredArgsConstructor
public class HiCadImportController {
    private final HiCadImportService imports;
    private final EinkaufBerechtigungService berechtigungen;

    @PostMapping("/vorschau")
    @ResponseStatus(HttpStatus.CREATED)
    public HiCadImportDto.Vorschau vorschau(@RequestParam Long projektId, @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) SpaltenMapping mapping, Authentication auth) {
        Long actor = berechtigungen.verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        return imports.vorschau(projektId, file, mapping, actor);
    }

    @PostMapping("/{id}/uebernehmen")
    public List<EinkaufBedarfDto.Response> uebernehmen(@PathVariable Long id,
            @RequestBody HiCadImportDto.Uebernahme request, Authentication auth) {
        Long actor = berechtigungen.verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        return imports.uebernehmen(id, request, actor);
    }

    @GetMapping("/{id}")
    public HiCadImportDto.ImportFortschritt fortschritt(@PathVariable Long id, Authentication auth) {
        Long actor = berechtigungen.verlange(auth, EinkaufBerechtigung.LESEN);
        return imports.fortschritt(id, actor);
    }

    @GetMapping("/{id}/bilder/{dateiId}")
    public ResponseEntity<Resource> ladeBild(@PathVariable Long id, @PathVariable Long dateiId, Authentication auth) {
        berechtigungen.verlange(auth, EinkaufBerechtigung.LESEN);
        HiCadImportService.ImportBildRessource image = imports.ladeBild(id, dateiId, auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mimeTyp()))
                .header("X-Content-Type-Options", "nosniff").body(image.resource());
    }
}
