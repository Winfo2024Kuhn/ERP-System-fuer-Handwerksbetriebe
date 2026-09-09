package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.DatevDto.Konfiguration;
import org.example.kalkulationsprogramm.service.DatevKonfigurationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/zeitverwaltung/monatsabschluesse/datev/konfiguration")
public class DatevKonfigurationController {
 private final DatevKonfigurationService service;
 @GetMapping public Konfiguration laden(Authentication auth) { return service.laden(auth); }
 @PutMapping public Konfiguration speichern(@RequestBody Konfiguration dto, Authentication auth) { return service.speichern(dto, auth); }
}
