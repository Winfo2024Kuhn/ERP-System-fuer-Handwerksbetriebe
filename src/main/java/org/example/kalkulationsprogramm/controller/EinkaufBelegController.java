package org.example.kalkulationsprogramm.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBelegDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBelegService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/einkauf/bestellungen/{id}/belege")
@RequiredArgsConstructor
public class EinkaufBelegController {
    private final EinkaufBelegService service;
    private final EinkaufBerechtigungService rechte;
    @GetMapping
    public List<Beleg> auflisten(@PathVariable Long id, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.auflisten(id);
    }
    @PostMapping("/{dokumentId}/datei")
    public Datei registrieren(@PathVariable Long id, @PathVariable Long dokumentId, Authentication auth) {
        return service.registrieren(id, dokumentId, rechte.verlange(auth, EinkaufBerechtigung.BEARBEITEN));
    }
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Datei hochladen(@PathVariable Long id, @RequestParam LieferantDokumentTyp typ,
            @RequestPart MultipartFile datei, Authentication auth) {
        return service.hochladen(id, typ, datei, rechte.verlange(auth, EinkaufBerechtigung.BEARBEITEN));
    }
}
