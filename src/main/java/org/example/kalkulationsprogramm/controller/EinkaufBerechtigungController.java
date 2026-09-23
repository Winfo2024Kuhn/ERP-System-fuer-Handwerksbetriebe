package org.example.kalkulationsprogramm.controller;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBerechtigungDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EinkaufBerechtigungController {
    private final EinkaufBerechtigungService berechtigungService;

    @GetMapping("/api/einkauf/berechtigungen")
    public Set<EinkaufBerechtigung> eigeneRechte(Authentication authentication) {
        return berechtigungService.rechte(authentication);
    }

    @GetMapping("/api/settings/einkauf-berechtigungen/{profileId}")
    public Set<EinkaufBerechtigung> profilRechte(Authentication authentication, @PathVariable Long profileId) {
        return berechtigungService.profilRechte(authentication, profileId);
    }

    @PutMapping("/api/settings/einkauf-berechtigungen/{profileId}")
    public Set<EinkaufBerechtigung> setzeProfilRechte(Authentication authentication, @PathVariable Long profileId,
            @RequestBody EinkaufBerechtigungDto request) {
        return berechtigungService.setzeRechte(authentication, profileId, request.rechte());
    }
}
