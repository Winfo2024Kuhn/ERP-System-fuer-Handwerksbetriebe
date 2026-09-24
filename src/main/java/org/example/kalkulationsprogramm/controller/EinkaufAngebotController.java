package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Angebot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Abweichungsfreigabe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Erfassung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.VersionDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf")
public class EinkaufAngebotController {
    private final EinkaufAngebotService service;
    private final EinkaufBerechtigungService rechte;
    public EinkaufAngebotController(EinkaufAngebotService service, EinkaufBerechtigungService rechte) {
        this.service = service; this.rechte = rechte;
    }
    @PostMapping("/anfrage-lieferanten/{id}/angebote")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionDto erfassen(@PathVariable Long id, @RequestBody Erfassung request, Authentication authentication) {
        return service.erfassen(id, request, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @GetMapping("/anfragen/{id}/angebote")
    public java.util.List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Uebersicht> auflisten(
            @PathVariable Long id, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return service.auflisten(id);
    }
    @GetMapping("/angebote/{id}")
    public Angebot laden(@PathVariable Long id, Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return service.laden(id);
    }
    @PutMapping("/angebote/{id}")
    public VersionDto aktualisieren(@PathVariable Long id, @RequestBody Erfassung request, Authentication authentication) {
        return service.neueVersion(id, request, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @PostMapping("/angebote/{id}/versionen")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionDto neueVersion(@PathVariable Long id, @RequestBody Erfassung request, Authentication authentication) {
        return service.neueVersion(id, request, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
    @PostMapping("/angebote/{id}/abweichung-bestaetigen")
    public VersionDto abweichungBestaetigen(@PathVariable Long id, @RequestParam long version,
            @RequestBody Abweichungsfreigabe request, Authentication authentication) {
        Long akteur = rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN);
        return service.abweichungBestaetigen(id, version, request == null ? null : request.begruendung(), akteur);
    }

    @PostMapping("/angebote/{id}/bestaetigen")
    public VersionDto bestaetigen(@PathVariable Long id, @RequestParam long version, Authentication authentication) {
        return service.bestaetigenAngebot(id, version, rechte.verlange(authentication, EinkaufBerechtigung.BEARBEITEN));
    }
}
