package org.example.kalkulationsprogramm.controller;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellfreigabeDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;import org.example.kalkulationsprogramm.service.einkauf.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/einkauf/bestellungen/{id}") public class EinkaufBestellfreigabeController {
 private final EinkaufBestellfreigabeService service;private final EinkaufBerechtigungService rechte;public EinkaufBestellfreigabeController(EinkaufBestellfreigabeService s,EinkaufBerechtigungService r){service=s;rechte=r;}
 @PostMapping("/vorschau") public Vorschau vorschau(@PathVariable Long id,@RequestParam Long templateId,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN);return service.vorschau(id,templateId);}
 @PostMapping("/freigeben") public VersandDto freigeben(@PathVariable Long id,@RequestBody Freigabe r,Authentication auth){return service.freigeben(id,r,rechte.verlange(auth,EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));}
 @PostMapping("/extern-gesendet") public void externGesendet(@PathVariable Long id,@RequestBody ExternerNachweis r,Authentication auth){service.externGesendet(id,r,rechte.verlange(auth,EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));}
 @PostMapping("/storno-bestaetigen") public StornoErgebnis storno(@PathVariable Long id,@RequestBody Storno r,Authentication auth){return service.stornoBestaetigen(id,r,rechte.verlange(auth,EinkaufBerechtigung.BESTELLUNG_FREIGEBEN));}
}
