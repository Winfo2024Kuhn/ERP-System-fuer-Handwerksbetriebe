package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.*;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.http.HttpStatus; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/einkauf/bestellungen")
public class EinkaufBestellungController {
 private final EinkaufBestellungService service; private final EinkaufBerechtigungService rechte;
 public EinkaufBestellungController(EinkaufBestellungService service,EinkaufBerechtigungService rechte){this.service=service;this.rechte=rechte;}
 @GetMapping public Page<Detail> suche(Pageable pageable,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.LESEN);return service.suche(pageable);}
 @GetMapping("/{id}") public Detail laden(@PathVariable Long id,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.LESEN);return service.lade(id);}
 @PostMapping("/aus-angebot") @ResponseStatus(HttpStatus.CREATED) public Detail ausAngebot(@RequestBody AusAngebot request,Authentication auth){return service.ausAngebot(request,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
 @PostMapping("/direkt") @ResponseStatus(HttpStatus.CREATED) public Detail direkt(@RequestBody Direkt request,Authentication auth){return service.direkt(request,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
 @PostMapping("/{id}/verwerfen") @ResponseStatus(HttpStatus.NO_CONTENT) public void verwerfen(@PathVariable Long id,@RequestParam long version,Authentication auth){service.verwerfen(id,version,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
 @PutMapping("/{id}") public Detail aendern(@PathVariable Long id,@RequestBody Aenderung request,Authentication auth){return service.aendern(id,request,rechte.verlange(auth,EinkaufBerechtigung.BEARBEITEN));}
 @GetMapping("/{id}/revisionen") public java.util.List<Revision> revisionen(@PathVariable Long id,Authentication auth){rechte.verlange(auth,EinkaufBerechtigung.LESEN);return service.revisionen(id);}
}
