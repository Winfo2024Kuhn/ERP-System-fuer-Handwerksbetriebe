package org.example.kalkulationsprogramm.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufStornoanfrageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf/bestellungen/{id}")
@RequiredArgsConstructor
public class EinkaufStornoanfrageController {
    private final EinkaufStornoanfrageService service;
    private final EinkaufBerechtigungService rechte;
    @PostMapping("/storno-vorschau")
    public Vorschau vorschau(@PathVariable Long id, @RequestBody Entwurf request, Authentication auth) {
        return service.vorschau(id, request, rechte.verlange(auth, EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));
    }
    @PostMapping("/storno-anfragen")
    public VersandDto freigeben(@PathVariable Long id, @RequestBody Freigabe request, Authentication auth) {
        return service.freigeben(id, request, rechte.verlange(auth, EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));
    }
    @PostMapping("/storno-anfragen/{versandId}/erneut")
    public VersandDto erneut(@PathVariable Long id, @PathVariable Long versandId,
            @RequestBody org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.VersandWiederholung request, Authentication auth) {
        return service.erneutSenden(id, versandId, request.version(), rechte.verlange(auth, EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));
    }
    @PostMapping("/storno-anfragen/{versandId}/klaeren")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void klaeren(@PathVariable Long id, @PathVariable Long versandId,
            @RequestBody org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.Klaerung request, Authentication auth) {
        service.klaeren(id, versandId, request, rechte.verlange(auth, EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));
    }
    @GetMapping("/storno-anfragen")
    public List<VersandDto> status(@PathVariable Long id, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.status(id);
    }
}
