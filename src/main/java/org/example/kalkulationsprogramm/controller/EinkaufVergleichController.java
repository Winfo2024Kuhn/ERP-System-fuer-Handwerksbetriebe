package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.Vergleich;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVergleichService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/einkauf/anfragen")
public class EinkaufVergleichController {
    private final EinkaufVergleichService vergleich;
    private final EinkaufBerechtigungService rechte;
    public EinkaufVergleichController(EinkaufVergleichService vergleich, EinkaufBerechtigungService rechte) {
        this.vergleich = vergleich; this.rechte = rechte;
    }
    @GetMapping("/{id}/vergleich")
    public Vergleich vergleichen(@PathVariable Long id, @RequestParam(required = false) LocalDate stichtag,
            Authentication authentication) {
        rechte.verlange(authentication, EinkaufBerechtigung.LESEN);
        return vergleich.vergleiche(id, stichtag == null ? LocalDate.now() : stichtag);
    }
}
