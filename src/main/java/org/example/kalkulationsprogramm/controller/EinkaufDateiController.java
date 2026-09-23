package org.example.kalkulationsprogramm.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/einkauf")
@RequiredArgsConstructor
public class EinkaufDateiController {
    private final EinkaufDateiService service;
    private final EinkaufBerechtigungService berechtigungen;

    @PostMapping("/bedarfe/{bedarfId}/anlagen")
    public ResponseEntity<AnlageDto> hochladen(@PathVariable Long bedarfId, @RequestPart("datei") MultipartFile datei,
            @RequestParam String revision, Authentication authentication) {
        Long actor = berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return ResponseEntity.status(201).body(service.hochladen(bedarfId, datei, revision, actor));
    }

    @PostMapping("/bedarfe/{bedarfId}/anlagen/email/{emailAttachmentId}")
    public ResponseEntity<AnlageDto> verknuepfeEmailAnlage(@PathVariable Long bedarfId,
            @PathVariable Long emailAttachmentId, @RequestParam String revision, Authentication authentication) {
        Long actor = berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return ResponseEntity.status(201).body(service.nutzeEmailAnlage(bedarfId, emailAttachmentId, revision, actor));
    }

    @PostMapping("/bedarfe/{bedarfId}/anlagen/lieferant-dokument/{dokumentId}")
    public ResponseEntity<AnlageDto> verknuepfeLieferantDokument(@PathVariable Long bedarfId,
            @PathVariable Long dokumentId, @RequestParam String revision, Authentication authentication) {
        Long actor = berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return ResponseEntity.status(201).body(service.nutzeLieferantDokument(bedarfId, dokumentId, revision, actor));
    }

    @GetMapping("/anlagen/{id}")
    public ResponseEntity<Resource> laden(@PathVariable Long id, Authentication authentication) {
        berechtigungen.verlange(authentication, EinkaufBerechtigung.LESEN);
        Resource resource = service.laden(id, authentication);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("einkauf-anlage", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(resource);
    }

    @PostMapping("/anlagen/{id}/freigeben")
    public AnlageDto freigeben(@PathVariable Long id, Authentication authentication) {
        Long actor = berechtigungen.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return service.freigeben(id, actor);
    }

    @PostMapping("/anlagen/paket-pruefen")
    public void pruefePaket(@RequestBody PaketPruefung request, Authentication authentication) {
        berechtigungen.verlange(authentication, EinkaufBerechtigung.LESEN);
        service.pruefePaketgroesse(request.versionIds(), request.pdfBytes());
    }

    public record PaketPruefung(List<Long> versionIds, long pdfBytes) {}
}
