package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufRechnungsabgleichDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufRechnungsabgleichService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf")
public class EinkaufRechnungsabgleichController {
    private final EinkaufRechnungsabgleichService service;
    private final EinkaufBerechtigungService rechte;
    public EinkaufRechnungsabgleichController(EinkaufRechnungsabgleichService service,EinkaufBerechtigungService rechte){this.service=service;this.rechte=rechte;}
    @GetMapping("/bestellungen/{id}/rechnungsabgleich")
    public ResponseEntity<Abgleich> vergleichen(@PathVariable Long id,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.LESEN);return ResponseEntity.ok(service.vergleichen(id));}
    @PostMapping("/belege/{dokumentId}/zuordnung")
    public ResponseEntity<?> zuordnen(@PathVariable Long dokumentId,@RequestBody BelegZuordnung request,Authentication auth){
        Long actor=rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN);
        var result=service.zuordnen(dokumentId,request,actor);
        return ResponseEntity.ok(java.util.Map.of("id",result.getId(),"dokumentId",dokumentId,"bestellungId",result.getBestellungId()));
    }
}
