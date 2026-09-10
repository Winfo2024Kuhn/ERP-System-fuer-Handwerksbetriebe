package org.example.kalkulationsprogramm.controller;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.*;
import org.example.kalkulationsprogramm.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/zeitverwaltung/monatsabschluesse")
public class MonatsabschlussUebersichtController {
    private final MonatsabschlussUebersichtService uebersicht;
    private final MonatsabschlussSammelService sammel;
    @GetMapping("/uebersicht") public Uebersicht lade(@RequestParam int jahr,@RequestParam int monat,@RequestParam(required=false) Long mitarbeiterId,@RequestParam(required=false) Long abteilungId,@RequestParam(defaultValue="ALLE") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,Authentication auth) { return uebersicht.lade(new Filter(jahr,monat,mitarbeiterId,abteilungId,status,page,size),auth); }
    @GetMapping("/vergleich") public List<Vergleichsmonat> vergleich(@RequestParam int jahr,@RequestParam int monat,@RequestParam(required=false) Long mitarbeiterId,@RequestParam(required=false) Long abteilungId,Authentication auth) { return uebersicht.vergleich(jahr,monat,mitarbeiterId,abteilungId,auth); }
    @GetMapping("/jahresvergleich") public Jahresvergleich jahresvergleich(@RequestParam int jahr,@RequestParam(required=false) Long mitarbeiterId,@RequestParam(required=false) Long abteilungId,Authentication auth) { return uebersicht.jahresvergleich(jahr,mitarbeiterId,abteilungId,auth); }
    @PostMapping("/sammelabschluss") public SammelResponse abschliessen(@RequestBody SammelRequest request,Authentication auth) { return sammel.abschliessen(request,auth); }
}
