package org.example.kalkulationsprogramm.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellstatusDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.BestaetigungDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBestellstatusService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf/bestellungen/{id}")
@RequiredArgsConstructor
public class EinkaufBestellstatusController {
    private final EinkaufBestellstatusService service;
    private final EinkaufBerechtigungService rechte;

    @GetMapping("/mengen")
    public List<Mengenstand> mengen(@PathVariable Long id, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.mengen(id);
    }
    @GetMapping("/lieferungen")
    public List<Lieferung> lieferungen(@PathVariable Long id, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.lieferungen(id);
    }
    @GetMapping("/bestaetigungen")
    public List<BestaetigungDto> bestaetigungen(@PathVariable Long id, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.bestaetigungen(id);
    }
    @GetMapping("/revisionen/{revisionId}/versandstatus")
    public List<VersandDto> versandstatus(@PathVariable Long id, @PathVariable Long revisionId, Authentication auth) {
        rechte.verlange(auth, EinkaufBerechtigung.LESEN); return service.versandstatus(id, revisionId);
    }
}
