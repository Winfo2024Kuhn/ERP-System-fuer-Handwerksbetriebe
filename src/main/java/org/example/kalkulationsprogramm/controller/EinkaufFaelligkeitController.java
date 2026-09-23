package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufFaelligkeitDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufFaelligkeitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/einkauf/faelligkeiten")
public class EinkaufFaelligkeitController {
    private final EinkaufFaelligkeitService service;
    private final EinkaufBerechtigungService rechte;
    public EinkaufFaelligkeitController(EinkaufFaelligkeitService service,EinkaufBerechtigungService rechte){this.service=service;this.rechte=rechte;}

    @GetMapping
    public Page<Faelligkeit> liste(@RequestParam(required=false) LocalDate heute,@RequestParam(required=false) Long zustaendigId,
            Pageable pageable,Authentication auth){
        rechte.verlange(auth,EinkaufBerechtigung.LESEN);
        return service.liste(heute==null?LocalDate.now():heute,zustaendigId,pageable);
    }

    @PostMapping("/nachfrage")
    public NachfrageEntwurf nachfrage(@RequestBody NachfrageRequest request,Authentication auth){
        rechte.verlange(auth,EinkaufBerechtigung.LESEN);
        return service.nachfrage(request.typ(),request.vorgangId(),request.beteiligungId());
    }
}
