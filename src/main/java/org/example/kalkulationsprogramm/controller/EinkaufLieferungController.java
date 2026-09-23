package org.example.kalkulationsprogramm.controller;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.*;import org.example.kalkulationsprogramm.service.einkauf.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/einkauf/bestellungen/{id}") public class EinkaufLieferungController {
 private final EinkaufLieferungService service;private final EinkaufBerechtigungService rechte;public EinkaufLieferungController(EinkaufLieferungService s,EinkaufBerechtigungService r){service=s;rechte=r;}
 @PostMapping("/lieferungen") public LieferungDto annehmen(@PathVariable Long id,@RequestBody Annahme r,Authentication auth){return service.annehmen(id,r,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
 @PostMapping("/bestaetigungen") public BestaetigungDto bestaetigung(@PathVariable Long id,@RequestBody Bestaetigung r,Authentication auth){return service.bestaetigungErfassen(id,r,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
}
