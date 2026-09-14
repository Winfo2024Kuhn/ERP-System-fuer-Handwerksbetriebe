package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.SteuerberaterPaketDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.SteuerberaterExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buchhaltung/steuerberater")
@RequiredArgsConstructor
public class SteuerberaterExportController {
    private final BelegService belegService;
    private final SteuerberaterExportService steuerberaterExportService;
    private final FirmeninformationRepository firmeninformationRepository;

    @GetMapping("/vorpruefung")
    public ResponseEntity<?> vorpruefung(@RequestParam int jahr, @RequestParam int monat,
                                         @RequestParam(value = "token", required = false) String token, Authentication auth) {
        Mitarbeiter caller = caller(token, auth);
        if (caller == null) return ResponseEntity.status(403).build();
        if (!gueltig(jahr, monat)) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Ungültiger Monat oder Jahr"));
        return ResponseEntity.ok(steuerberaterExportService.pruefe(jahr, monat));
    }

    @GetMapping("/paket")
    public ResponseEntity<?> paket(@RequestParam int jahr, @RequestParam int monat,
                                   @RequestParam(defaultValue = "false") boolean trotzdem,
                                   @RequestParam(value = "token", required = false) String token, Authentication auth) {
        Mitarbeiter caller = caller(token, auth);
        if (caller == null) return ResponseEntity.status(403).build();
        if (!gueltig(jahr, monat)) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Ungültiger Monat oder Jahr"));
        SteuerberaterPaketDto.Vorpruefung pruefung = steuerberaterExportService.pruefe(jahr, monat);
        if (!trotzdem && !pruefung.getOffenePunkte().isEmpty()) return ResponseEntity.status(409).body(pruefung);
        byte[] zip = steuerberaterExportService.erzeugeZip(jahr, monat, caller, trotzdem);
        String firma = firmeninformationRepository.findFirmeninformation().map(f -> f.getFirmenname()).orElse("Betrieb");
        String name = firma.replaceAll("[^A-Za-z0-9-_]", "_");
        if (name.isBlank()) name = "Betrieb";
        String filename = String.format("%04d-%02d_Kasse_%s.zip", jahr, monat, name);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip")).body(zip);
    }

    private Mitarbeiter caller(String token, Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        return caller != null && belegService.darfSehen(caller) ? caller : null;
    }
    private static boolean gueltig(int jahr, int monat) { return jahr >= 2000 && jahr <= 2999 && monat >= 1 && monat <= 12; }
}
