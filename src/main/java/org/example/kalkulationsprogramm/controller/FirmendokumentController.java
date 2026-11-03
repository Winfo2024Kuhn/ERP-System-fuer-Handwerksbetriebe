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

/**
 * Firmendokumente (alle Formate außer Programmdateien) unter Stammdaten. Jeder angemeldete PC-Benutzer; was er
 * sehen und ändern darf, entscheiden die Ordnerrechte im Dienst (unsichtbar → 404, nur ansehen → 403 beim Ändern).
 * Dateien kommen mit {@code nosniff} und {@code Content-Security-Policy: sandbox; default-src 'none'} (wie
 * {@code EmailSignatureController}); nur PDF und Bild dürfen im
 * Browser geöffnet werden, alles andere ist immer ein Download.
 */
@RestController
@RequestMapping("/api/firmendokumente")
@RequiredArgsConstructor
public class FirmendokumentController {

    private final FirmendokumentService service;

    @GetMapping
    public FirmendokumentSeiteDto imOrdner(@RequestParam Long ordnerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return service.imOrdner(ordnerId, page, size, authentication);
    }

    @GetMapping("/suche")
    public FirmendokumentSeiteDto suche(@RequestParam(name = "q", required = false) String suchtext,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return service.suche(suchtext, page, size, authentication);
    }

    @GetMapping("/faellig")
    public FirmendokumentSeiteDto faellig(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, Authentication authentication) {
        return service.faellig(page, size, authentication);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FirmendokumentDto> hochladen(@RequestParam(name = "datei", required = false) MultipartFile datei,
            @RequestParam(required = false) Long ordnerId, @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate faelligAm,
            Authentication authentication) {
        FirmendokumentDto dto = service.hochladen(datei, ordnerId, name, faelligAm, anzeigename(authentication),
                authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public FirmendokumentDto aendern(@PathVariable Long id, @RequestBody FirmendokumentAendernDto eingabe,
            Authentication authentication) {
        return service.aendern(id, eingabe, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id, Authentication authentication) {
        service.loeschen(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/datei")
    public ResponseEntity<Resource> datei(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download, Authentication authentication) {
        FirmendokumentService.Datei datei = service.datei(id, authentication);
        boolean alsAnhang = download || datei.immerAlsAnhang();
        ContentDisposition disposition = (alsAnhang ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(datei.dateiname(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(datei.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(new FileSystemResource(datei.pfad()));
    }

    private static String anzeigename(Authentication authentication) {
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        return principal == null ? null : principal.getDisplayName();
    }
}
