package org.example.kalkulationsprogramm.controller;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung; import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPreisUebernahmeDto.PreisUebernahme; import org.example.kalkulationsprogramm.service.einkauf.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/einkauf/angebote")
public class EinkaufPreisUebernahmeController {
 private final EinkaufPreisUebernahmeService service; private final EinkaufBerechtigungService rechte;
 public EinkaufPreisUebernahmeController(EinkaufPreisUebernahmeService service,EinkaufBerechtigungService rechte){this.service=service;this.rechte=rechte;}
 @PostMapping("/{id}/positionen/{positionId}/preis-uebernehmen") public org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto uebernehmen(@PathVariable Long id,@PathVariable Long positionId,@RequestBody PreisUebernahme request,Authentication auth){return service.uebernehmen(id,positionId,request,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
}
