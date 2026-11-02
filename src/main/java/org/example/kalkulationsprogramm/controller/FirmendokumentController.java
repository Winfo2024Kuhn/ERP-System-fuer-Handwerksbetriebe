package org.example.kalkulationsprogramm.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


import lombok.RequiredArgsConstructor;

/** Firmen-PDFs unter Stammdaten. Jeder angemeldete PC-Benutzer darf lesen, ablegen, ändern und löschen. */
@RestController
@RequestMapping("/api/firmendokumente")
@RequiredArgsConstructor
public class FirmendokumentController {

    private final FirmendokumentService service;

    @GetMapping
    public FirmendokumentSeiteDto imOrdner(@RequestParam Long ordnerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.imOrdner(ordnerId, page, size);
    }

    @GetMapping("/suche")
    public FirmendokumentSeiteDto suche(@RequestParam(name = "q", required = false) String suchtext,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.suche(suchtext, page, size);
    }

    @GetMapping("/faellig")
    public FirmendokumentSeiteDto faellig(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.faellig(page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FirmendokumentDto> hochladen(@RequestParam(name = "datei", required = false) MultipartFile datei,
            @RequestParam(required = false) Long ordnerId, @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate faelligAm,
            Authentication authentication) {
        FirmendokumentDto dto = service.hochladen(datei, ordnerId, name, faelligAm, anzeigename(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public FirmendokumentDto aendern(@PathVariable Long id, @RequestBody FirmendokumentAendernDto eingabe) {
        return service.aendern(id, eingabe);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/datei")
    public ResponseEntity<Resource> datei(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download) {
        FirmendokumentService.Datei datei = service.datei(id);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(datei.dateiname(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(datei.pfad()));
    }

    private static String anzeigename(Authentication authentication) {
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        return principal == null ? null : principal.getDisplayName();
    }
}
